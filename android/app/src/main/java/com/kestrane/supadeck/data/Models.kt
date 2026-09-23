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
fun JSONObject.dblOrNull(key: String): Double? = if (!has(key) || isNull(key)) null else optDouble(key)

private fun cell(v: Any?): String = if (v == null || v == JSONObject.NULL) "NULL" else v.toString()

/**
 * Builds a display grid from raw query result rows. Pass an explicit column order when it's
 * known (e.g. from a schema lookup); otherwise columns are inferred from the union of keys
 * across the rows, in first-seen order, which is what the free-form SQL tab uses.
 */
fun List<JSONObject>.toGrid(columns: List<String>? = null): Grid {
    val names = columns ?: run {
        val seen = LinkedHashSet<String>()
        forEach { row -> row.keys().forEach { seen.add(it) } }
        seen.toList()
    }
    val rows = map { row -> names.map { c -> cell(row.opt(c)) } }
    return Grid(columns = names, rows = rows)
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

// ---------- SQL building helpers ----------
// The app builds SQL client-side now (no server-side query builder), so identifiers and
// literals it splices in itself — schema/table names discovered from earlier queries, ids
// selected from a list — are escaped here. The free-form SQL tab is the person's own input
// and is sent as-is, matching what the dashboard's SQL Editor does.

fun quoteIdent(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""
fun quoteLiteral(s: String): String = "'" + s.replace("'", "''") + "'"
