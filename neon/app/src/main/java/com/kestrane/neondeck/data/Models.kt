package com.kestrane.neondeck.data

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
)

data class Overview(
    val dbBytes: Double,
    val connections: Int,
    val tables: Int,
    val version: String,
    val cacheHit: Double?,
    val topTables: List<Triple<String, Double, Double>>, // schema.name, bytes, rows
    val databases: List<Pair<String, Double>>, // name, bytes — every database on this branch
)

// ---------- JSON helpers ----------

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

fun JSONObject.toTable(): TableInfo =
    TableInfo(str("schema"), str("name"), str("kind"), optLong("est_rows"), optBoolean("rls"))

fun JSONObject.toColumn(): ColumnInfo = ColumnInfo(str("name"), str("type"), optBoolean("nullable"))

// ---------- SQL building helpers ----------
// The app builds SQL client-side — schema/table names it discovered itself get escaped as
// identifiers before being spliced back into a query. The free-form SQL tab sends the person's
// own input as-is, same as any SQL client would.

fun quoteIdent(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""
fun quoteLiteral(s: String): String = "'" + s.replace("'", "''") + "'"
