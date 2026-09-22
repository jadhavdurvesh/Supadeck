package com.kestrane.supadeck.data

import org.json.JSONArray
import org.json.JSONObject

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val data: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

data class TableInfo(val schema: String, val name: String, val kind: String, val estRows: Long, val rls: Boolean)
data class ColumnInfo(val name: String, val type: String, val nullable: Boolean)

/** Rows already flattened to strings so any query result can be shown in the same grid. */
data class Grid(
    val columns: List<String>,
    val rows: List<List<String>>,
    val hasMore: Boolean = false,
    val note: String? = null,
)

data class UserRow(
    val id: String,
    val email: String,
    val phone: String,
    val createdAt: String,
    val lastSignIn: String,
    val confirmed: Boolean,
    val bannedUntil: String,
    val provider: String,
    val metadata: String,
)

data class UsersPage(val users: List<UserRow>, val hasMore: Boolean)
data class LogLine(val time: String, val message: String)
data class LogsResult(val configured: Boolean, val lines: List<LogLine>, val error: String?)

data class Overview(
    val dbBytes: Double,
    val connections: Int,
    val tables: Int,
    val version: String,
    val cacheHit: Double?,
    val usersTotal: Int,
    val active7d: Int,
    val new24h: Int,
    val signups: List<Pair<String, Int>>,
    val storage: List<Triple<String, Int, Double>>, // bucket, objects, bytes
    val topTables: List<Triple<String, Double, Double>>, // name, bytes, rows
)

// ---------- JSON helpers ----------

inline fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return List(length()) { f(getJSONObject(it)) }
}

fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key)
fun JSONObject.dbl(key: String): Double = if (isNull(key)) 0.0 else optDouble(key, 0.0)

private fun cell(v: Any?): String = if (v == null || v == JSONObject.NULL) "NULL" else v.toString()

fun JSONObject.toGrid(): Grid {
    val names = getJSONArray("columns").let { a -> List(a.length()) { a.getString(it) } }
    val rows = getJSONArray("rows").mapObjects { r -> names.map { c -> cell(r.opt(c)) } }
    return Grid(
        columns = names,
        rows = rows,
        hasMore = optBoolean("has_more") || optBoolean("truncated"),
        note = if (has("ms")) "${optLong("ms")} ms" else null,
    )
}

fun JSONObject.toOverview(): Overview {
    val db = getJSONObject("db")
    val u = getJSONObject("users")
    return Overview(
        dbBytes = db.dbl("db_bytes"),
        connections = db.optInt("connections"),
        tables = optInt("tables"),
        version = db.str("version").substringBefore(" on "),
        cacheHit = if (isNull("cache_hit")) null else optDouble("cache_hit"),
        usersTotal = u.optInt("total"),
        active7d = u.optInt("active_7d"),
        new24h = u.optInt("new_24h"),
        signups = getJSONArray("signups").mapObjects { it.str("day") to it.optInt("count") },
        storage = getJSONArray("storage").mapObjects { Triple(it.str("name"), it.optInt("objects"), it.dbl("bytes")) },
        topTables = getJSONArray("top_tables").mapObjects {
            Triple("${it.str("schema")}.${it.str("name")}", it.dbl("bytes"), it.dbl("rows"))
        },
    )
}

fun JSONObject.toUser(): UserRow = UserRow(
    id = str("id"),
    email = str("email"),
    phone = str("phone"),
    createdAt = str("created_at"),
    lastSignIn = str("last_sign_in_at"),
    confirmed = optBoolean("confirmed"),
    bannedUntil = str("banned_until"),
    provider = str("provider"),
    metadata = if (isNull("metadata")) "" else get("metadata").toString(),
)

fun JSONObject.toTable(): TableInfo =
    TableInfo(str("schema"), str("name"), str("kind"), optLong("est_rows"), optBoolean("rls"))

fun JSONObject.toColumn(): ColumnInfo = ColumnInfo(str("name"), str("type"), optBoolean("nullable"))
