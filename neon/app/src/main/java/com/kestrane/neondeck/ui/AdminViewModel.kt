package com.kestrane.neondeck.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kestrane.neondeck.data.Api
import com.kestrane.neondeck.data.ApiException
import com.kestrane.neondeck.data.ColumnInfo
import com.kestrane.neondeck.data.Grid
import com.kestrane.neondeck.data.Load
import com.kestrane.neondeck.data.Overview
import com.kestrane.neondeck.data.Store
import com.kestrane.neondeck.data.TableInfo
import com.kestrane.neondeck.data.dbl
import com.kestrane.neondeck.data.dblOrNull
import com.kestrane.neondeck.data.quoteIdent
import com.kestrane.neondeck.data.quoteLiteral
import com.kestrane.neondeck.data.str
import com.kestrane.neondeck.data.toColumn
import com.kestrane.neondeck.data.toGrid
import com.kestrane.neondeck.data.toTable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AdminViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val api = Api(store)
    private val jobs = mutableMapOf<String, Job>()

    // ----- connection -----
    var signedIn by mutableStateOf(api.isSignedIn); private set
    var connectBusy by mutableStateOf(false); private set
    var connectError by mutableStateOf<String?>(null); private set
    val savedConnectionString: String get() = store.connectionString

    // ----- screen state -----
    var overview by mutableStateOf<Load<Overview>>(Load.Loading); private set
    var tables by mutableStateOf<Load<List<TableInfo>>>(Load.Loading); private set
    var selected by mutableStateOf<TableInfo?>(null); private set
    var rows by mutableStateOf<Load<Grid>>(Load.Loading); private set
    var loadingMore by mutableStateOf(false); private set
    var columns by mutableStateOf<Load<List<ColumnInfo>>>(Load.Loading); private set
    var sqlResult by mutableStateOf<Load<Grid>?>(null); private set

    private var tableColumns: List<String> = emptyList()

    // ----- helpers -----
    private suspend fun <T> guarded(block: suspend () -> T): Load<T> = try {
        Load.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Load.Err(e.message ?: "Request failed")
    }

    private fun launchOnce(key: String, force: Boolean, isLoaded: Boolean, block: suspend () -> Unit) {
        if (!force && (isLoaded || jobs[key]?.isActive == true)) return
        jobs[key]?.cancel()
        jobs[key] = viewModelScope.launch { block() }
    }

    private suspend fun one(query: String) = api.sql(query).firstOrNull()

    // ----- connection -----
    fun connect(connectionString: String) {
        viewModelScope.launch {
            connectBusy = true
            connectError = null
            try {
                api.connect(connectionString)
                api.sql("select 1", readOnly = true) // fail fast if it's wrong
                resetData()
                signedIn = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                api.signOut()
                connectError = when (e) {
                    is ApiException -> e.message
                    else -> e.message?.substringBefore('\n') ?: "Couldn't connect"
                } ?: "Couldn't connect"
            }
            connectBusy = false
        }
    }

    fun signOut() {
        api.signOut()
        signedIn = false
        resetData()
    }

    private fun resetData() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        overview = Load.Loading
        tables = Load.Loading
        selected = null
        sqlResult = null
    }

    fun refresh(tab: Tab) {
        when (tab) {
            Tab.Overview -> loadOverview(true)
            Tab.Tables -> if (selected != null) loadRows(reset = true) else loadTables(true)
            Tab.Sql -> Unit
        }
    }

    // ----- overview -----
    fun loadOverview(force: Boolean = false) = launchOnce("overview", force, overview is Load.Ok) {
        if (force) overview = Load.Loading
        overview = guarded {
            val db = one(
                "select pg_database_size(current_database())::float8 as db_bytes, " +
                    "(select count(*) from pg_stat_activity where datname = current_database())::int as connections, " +
                    "version() as version",
            )
            val tableCount = one(
                "select count(*)::int as n from pg_class c join pg_namespace n on n.oid = c.relnamespace " +
                    "where c.relkind in ('r','p') and n.nspname = 'public'",
            )
            val cache = one(
                "select round(100 * sum(heap_blks_hit) / nullif(sum(heap_blks_hit) + sum(heap_blks_read), 0), 2)::float8 as cache_hit " +
                    "from pg_statio_user_tables",
            )
            val topTableRows = api.sql(
                "select schemaname as schema, relname as name, pg_total_relation_size(relid)::float8 as bytes, " +
                    "n_live_tup::float8 as rows from pg_stat_user_tables order by bytes desc limit 8",
            )
            val dbRows = api.sql(
                "select datname as name, pg_database_size(datname)::float8 as bytes from pg_database " +
                    "where not datistemplate order by bytes desc",
            )

            Overview(
                dbBytes = db?.dbl("db_bytes") ?: 0.0,
                connections = db?.optInt("connections") ?: 0,
                tables = tableCount?.optInt("n") ?: 0,
                version = (db?.str("version") ?: "").substringBefore(" on "),
                cacheHit = cache?.dblOrNull("cache_hit"),
                topTables = topTableRows.map { Triple("${it.str("schema")}.${it.str("name")}", it.dbl("bytes"), it.dbl("rows")) },
                databases = dbRows.map { it.str("name") to it.dbl("bytes") },
            )
        }
    }

    // ----- tables -----
    fun loadTables(force: Boolean = false) = launchOnce("tables", force, tables is Load.Ok) {
        if (force) tables = Load.Loading
        tables = guarded {
            api.sql(
                "select n.nspname as schema, c.relname as name, " +
                    "case c.relkind when 'r' then 'table' when 'v' then 'view' when 'm' then 'materialized view' when 'p' then 'partitioned table' end as kind, " +
                    "c.reltuples::float8 as est_rows, c.relrowsecurity as rls " +
                    "from pg_class c join pg_namespace n on n.oid = c.relnamespace " +
                    "where c.relkind in ('r','v','m','p') and n.nspname not in ('pg_catalog','information_schema') and n.nspname not like 'pg\\_%' " +
                    "order by (n.nspname = 'public') desc, n.nspname, c.relname",
            ).map { it.toTable() }
        }
    }

    fun openTable(t: TableInfo) {
        selected = t
        rows = Load.Loading
        columns = Load.Loading
        tableColumns = emptyList()
        viewModelScope.launch {
            val result = guarded {
                api.sql(
                    "select a.attname as name, format_type(a.atttypid, a.atttypmod) as type, not a.attnotnull as nullable " +
                        "from pg_attribute a join pg_class c on c.oid = a.attrelid join pg_namespace n on n.oid = c.relnamespace " +
                        "where n.nspname = ${quoteLiteral(t.schema)} and c.relname = ${quoteLiteral(t.name)} " +
                        "and c.relkind in ('r','v','m','p') and a.attnum > 0 and not a.attisdropped order by a.attnum",
                ).map { it.toColumn() }
            }
            columns = result
            tableColumns = (result as? Load.Ok)?.data?.map { it.name } ?: emptyList()
            loadRows(reset = true)
        }
    }

    fun closeTable() {
        selected = null
    }

    fun loadRows(reset: Boolean = false) {
        val t = selected ?: return
        if (tableColumns.isEmpty()) return
        val current = (rows as? Load.Ok)?.data
        val offset = if (reset || current == null) 0 else current.rows.size
        jobs["rows"]?.cancel()
        jobs["rows"] = viewModelScope.launch {
            if (offset > 0) loadingMore = true else rows = Load.Loading
            val cols = tableColumns
            val select = cols.joinToString(", ") { quoteIdent(it) }
            val from = "${quoteIdent(t.schema)}.${quoteIdent(t.name)}"
            val query = "select $select from $from limit ${PAGE + 1} offset $offset"
            val res = guarded { api.sql(query).let { it.take(PAGE).toGrid(cols).copy(hasMore = it.size > PAGE) } }
            rows = when {
                offset > 0 && current != null && res is Load.Ok ->
                    Load.Ok(res.data.copy(rows = current.rows + res.data.rows))
                offset > 0 && current != null && res is Load.Err -> Load.Ok(current)
                else -> res
            }
            loadingMore = false
        }
    }

    // ----- SQL -----
    fun runSql(query: String) {
        jobs["sql"]?.cancel()
        jobs["sql"] = viewModelScope.launch {
            sqlResult = Load.Loading
            sqlResult = guarded { api.sql(query, readOnly = true).toGrid() }
        }
    }

    private companion object {
        const val PAGE = 50
    }
}
