package com.kestrane.neondeck.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLDecoder
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.util.Properties

class ApiException(message: String) : Exception(message)

data class ParsedConnection(
    val host: String,
    val port: Int,
    val database: String,
    val user: String,
    val password: String,
    val sslMode: String,
)

/** Parses a standard postgres(ql):// connection string, the same format Neon gives you. */
fun parseConnectionString(raw: String): ParsedConnection {
    val s = raw.trim()
    val withoutScheme = when {
        s.startsWith("postgresql://") -> s.removePrefix("postgresql://")
        s.startsWith("postgres://") -> s.removePrefix("postgres://")
        else -> throw ApiException("Connection string must start with postgresql:// or postgres://")
    }
    val atIndex = withoutScheme.lastIndexOf('@')
    if (atIndex < 0) throw ApiException("Missing user:password@host in the connection string")
    val userInfo = withoutScheme.substring(0, atIndex)
    val rest = withoutScheme.substring(atIndex + 1)

    val colonIndex = userInfo.indexOf(':')
    if (colonIndex < 0) throw ApiException("Missing password in the connection string")
    val user = URLDecoder.decode(userInfo.substring(0, colonIndex), "UTF-8")
    val password = URLDecoder.decode(userInfo.substring(colonIndex + 1), "UTF-8")

    val questionIndex = rest.indexOf('?')
    val hostDbPart = if (questionIndex >= 0) rest.substring(0, questionIndex) else rest
    val queryPart = if (questionIndex >= 0) rest.substring(questionIndex + 1) else ""

    val slashIndex = hostDbPart.indexOf('/')
    if (slashIndex < 0) throw ApiException("Missing database name in the connection string")
    val hostPort = hostDbPart.substring(0, slashIndex)
    val database = hostDbPart.substring(slashIndex + 1)
    if (database.isBlank()) throw ApiException("Missing database name in the connection string")

    val hostPortParts = hostPort.split(":")
    val host = hostPortParts[0]
    if (host.isBlank()) throw ApiException("Missing host in the connection string")
    val port = hostPortParts.getOrNull(1)?.toIntOrNull() ?: 5432

    val params = queryPart.split("&").filter { it.isNotBlank() }.associate {
        val kv = it.split("=", limit = 2)
        kv[0] to (kv.getOrNull(1) ?: "")
    }
    val sslMode = params["sslmode"] ?: "require"

    return ParsedConnection(host, port, database, user, password, sslMode)
}

/**
 * Talks straight to Postgres over the wire protocol — no Neon-specific API involved. Neon
 * databases are standard Postgres, so a plain JDBC connection sees everything: every schema,
 * every table, full read/write if you ask for it. The SQL tab keeps every query read-only via
 * a real Postgres read-only transaction, enforced by the database itself.
 *
 * One connection is kept open and reused across calls (each new TLS handshake against Neon can
 * take a second or more, longer still if the compute had auto-suspended). If a query fails
 * because the connection went stale, it's transparently reopened and retried once.
 */
class Api(private val store: Store) {
    @Volatile private var conn: Connection? = null
    private val lock = Any()

    init {
        Class.forName("org.postgresql.Driver")
    }

    val isSignedIn: Boolean get() = store.isSignedIn

    fun connect(raw: String) {
        parseConnectionString(raw) // throws ApiException if malformed
        store.connectionString = raw.trim()
    }

    fun signOut() {
        closeQuietly()
        store.clear()
    }

    suspend fun sql(query: String, readOnly: Boolean = true): List<JSONObject> =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                try {
                    runOnce(query, readOnly)
                } catch (e: SQLException) {
                    closeQuietly()
                    try {
                        runOnce(query, readOnly)
                    } catch (e2: SQLException) {
                        throw ApiException(e2.message ?: "Query failed")
                    }
                }
            }
        }

    private fun runOnce(query: String, readOnly: Boolean): List<JSONObject> {
        val c = ensureConnection()
        c.isReadOnly = readOnly
        val st = c.createStatement()
        try {
            val hasResult = st.execute(query)
            if (!hasResult) return emptyList()
            val rs = st.resultSet
            try {
                return resultSetToJson(rs)
            } finally {
                rs.close()
            }
        } finally {
            st.close()
        }
    }

    private fun ensureConnection(): Connection {
        val existing = conn
        if (existing != null && !existing.isClosed) return existing
        val p = parseConnectionString(store.connectionString)
        val url = "jdbc:postgresql://${p.host}:${p.port}/${p.database}"
        val props = Properties().apply {
            setProperty("user", p.user)
            setProperty("password", p.password)
            setProperty("sslmode", p.sslMode)
            setProperty("connectTimeout", "20")
            setProperty("socketTimeout", "30")
            setProperty("ApplicationName", "NeonDeck")
        }
        val fresh = try {
            DriverManager.getConnection(url, props)
        } catch (e: SQLException) {
            throw ApiException(e.message ?: "Couldn't connect to the database")
        }
        conn = fresh
        return fresh
    }

    private fun closeQuietly() {
        try {
            conn?.close()
        } catch (_: Exception) {
        }
        conn = null
    }

    private fun resultSetToJson(rs: ResultSet): List<JSONObject> {
        val meta = rs.metaData
        val count = meta.columnCount
        val names = (1..count).map { meta.getColumnLabel(it) }
        val out = mutableListOf<JSONObject>()
        while (rs.next()) {
            val row = JSONObject()
            for (i in 1..count) {
                row.put(names[i - 1], sqlValueToJson(rs.getObject(i)))
            }
            out.add(row)
        }
        return out
    }

    private fun sqlValueToJson(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is Boolean, is Int, is Long, is Double, is Float, is Short -> v
        is java.math.BigDecimal -> v.toPlainString()
        is ByteArray -> "\\x" + v.joinToString("") { "%02x".format(it) }
        else -> v.toString()
    }
}
