package com.kestrane.supadeck.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kestrane.supadeck.data.Api
import com.kestrane.supadeck.data.ApiException
import com.kestrane.supadeck.data.ColumnInfo
import com.kestrane.supadeck.data.Grid
import com.kestrane.supadeck.data.Load
import com.kestrane.supadeck.data.LogsResult
import com.kestrane.supadeck.data.Overview
import com.kestrane.supadeck.data.Store
import com.kestrane.supadeck.data.TableInfo
import com.kestrane.supadeck.data.UsersPage
import com.kestrane.supadeck.data.dbl
import com.kestrane.supadeck.data.dblOrNull
import com.kestrane.supadeck.data.quoteIdent
import com.kestrane.supadeck.data.quoteLiteral
import com.kestrane.supadeck.data.str
import com.kestrane.supadeck.data.toColumn
import com.kestrane.supadeck.data.toGrid
import com.kestrane.supadeck.data.toTable
import com.kestrane.supadeck.data.toUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val LOG_SOURCES = setOf("edge_logs", "postgres_logs", "auth_logs", "function_logs")

class AdminViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val api = Api(store)
    private val jobs = mutableMapOf<String, Job>()

    // ----- connection -----
    var signedIn by mutableStateOf(api.isSignedIn); private set
    var connectBusy by mutableStateOf(false); private set
    var connectError by mutableStateOf<String?>(null); private set
    val savedUrl: String get() = store.projectUrl

    // ----- screen state -----
    var overview by mutableStateOf<Load<Overview>>(Load.Loading); private set
    var tables by mutableStateOf<Load<List<TableInfo>>>(Load.Loading); private set
    var selected by mutableStateOf<TableInfo?>(null); private set
    var rows by mutableStateOf<Load<Grid>>(Load.Loading); private set
    var loadingMore by mutableStateOf(false); private set
    var columns by mutableStateOf<Load<List<ColumnInfo>>>(Load.Loading); private set
    var users by mutableStateOf<Load<UsersPage>>(Load.Loading); private set
    var sqlResult by mutableStateOf<Load<Grid>?>(null); private set
    var logs by mutableStateOf<Load<LogsResult>>(Load.Loading); private set
    var logSource by mutableStateOf("edge_logs"); private set

    private var userQuery = ""
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

    private fun toast(message: String) =
        Toast.makeText(getApplication<Application>(), message, Toast.LENGTH_LONG).show()

    private suspend fun one(query: String) = api.sql(query).firstOrNull()

    // ----- connection -----
    fun connect(url: String, token: String) {
        viewModelScope.launch {
            connectBusy = true
            connectError = null
            try {
                api.connect(url, token)
                // Prove the token actually works before switching screens.
                api.sql("select 1", readOnly = true)
                resetData()
                signedIn = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                api.signOut()
                connectError = when {
                    e is ApiException && e.code == 401 -> "That token was rejected. Check it and try again."
                    e is ApiException && e.code == 404 -> "Project not found. Check the project ref in the URL."
                    else -> e.message ?: "Couldn't connect"
                }
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
        users = Load.Loading
        sqlResult = null
        logs = Load.Loading
    }

    fun refresh(tab: Tab) {
        when (tab) {
            Tab.Overview -> loadOverview(true)
            Tab.Tables -> if (selected != null) loadRows(reset = true) else loadTables(true)
            Tab.Users -> fetchUsers(reset = true)
            Tab.Sql -> Unit
            Tab.Logs -> loadLogs(true)
        }
    }

    // ----- overview -----
    fun loadOverview(force: Boolean = false) = launchOnce("overview", force, overview is Load.Ok) {
        if (force) overview = Load.Loading
        overview = guarded {
            val db = one("select pg_database_size(current_database())::float8 as db_bytes, (select count(*) from pg_stat_activity where datname = current_database())::int as connections, version() as version")
            val tableCount = one("select count(*)::int as n from pg_class c join pg_namespace n on n.oid = c.relnamespace where c.relkind in ('r','p') and n.nspname = 'public'")
            val userStats = one("select count(*)::int as total, (count(*) filter (where last_sign_in_at > now() - interval '7 days'))::int as active_7d, (count(*) filter (where created_at > now() - interval '24 hours'))::int as new_24h from auth.users")
            val signupRows = api.sql(
                "select to_char(d, 'MM-DD') as day, coalesce(u.c, 0)::int as count from generate_series((current_date - 13)::timestamp, current_date::timestamp, interval '1 day') as d left join (select created_at::date as day, count(*) as c from auth.users group by 1) u on u.day = d::date order by d",
            )
            val storageRows = api.sql(
                "select b.name, count(o.id)::int as objects, coalesce(sum((o.metadata->>'size')::bigint), 0)::float8 as bytes from storage.buckets b left join storage.objects o on o.bucket_id = b.id group by b.name order by bytes desc limit 10",
            )
            val topTableRows = api.sql(
                "select schemaname as schema, relname as name, pg_total_relation_size(relid)::float8 as bytes, n_live_tup::float8 as rows from pg_stat_user_tables order by bytes desc limit 8",
            )
            val cache = one("select round(100 * sum(heap_blks_hit) / nullif(sum(heap_blks_hit) + sum(heap_blks_read), 0), 2)::float8 as cache_hit from pg_statio_user_tables")

            Overview(
                dbBytes = db?.dbl("db_bytes") ?: 0.0,
                connections = db?.optInt("connections") ?: 0,
                tables = tableCount?.optInt("n") ?: 0,
                version = (db?.str("version") ?: "").substringBefore(" on "),
                cacheHit = cache?.dblOrNull("cache_hit"),
                usersTotal = userStats?.optInt("total") ?: 0,
                active7d = userStats?.optInt("active_7d") ?: 0,
                new24h = userStats?.optInt("new_24h") ?: 0,
                signups = signupRows.map { it.str("day") to it.optInt("count") },
                storage = storageRows.map { Triple(it.str("name"), it.optInt("objects"), it.dbl("bytes")) },
                topTables = topTableRows.map { Triple("${it.str("schema")}.${it.str("name")}", it.dbl("bytes"), it.dbl("rows")) },
            )
        }
    }

    // ----- tables -----
    fun loadTables(force: Boolean = false) = launchOnce("tables", force, tables is Load.Ok) {
        if (force) tables = Load.Loading
        tables = guarded {
            api.sql(
                "select n.nspname as schema, c.relname as name, case c.relkind when 'r' then 'table' when 'v' then 'view' when 'm' then 'materialized view' when 'p' then 'partitioned table' end as kind, c.reltuples::float8 as est_rows, c.relrowsecurity as rls from pg_class c join pg_namespace n on n.oid = c.relnamespace where c.relkind in ('r','v','m','p') and n.nspname not in ('pg_catalog','information_schema') and n.nspname not like 'pg\\_%' order by (n.nspname = 'public') desc, n.nspname, c.relname",
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
                    "select a.attname as name, format_type(a.atttypid, a.atttypmod) as type, not a.attnotnull as nullable from pg_attribute a join pg_class c on c.oid = a.attrelid join pg_namespace n on n.oid = c.relnamespace where n.nspname = ${quoteLiteral(t.schema)} and c.relname = ${quoteLiteral(t.name)} and c.relkind in ('r','v','m','p') and a.attnum > 0 and not a.attisdropped order by a.attnum",
                ).map { it.toColumn() }
            }
            columns = result
            tableColumns = (result as? Load.Ok)?.data
                ?.map { it.name }
                ?.let { names -> if (t.schema == "auth") names.filterNot { Regex("password|token|secret", RegexOption.IGNORE_CASE).containsMatchIn(it) } else names }
                ?: emptyList()
            loadRows(reset = true)
        }
    }

    fun closeTable() {
        selected = null
    }

    fun loadRows(reset: Boolean = false) {
        val t = selected ?: return
        if (tableColumns.isEmpty()) return // columns still loading; openTable() will call us again
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

    // ----- users -----
    fun searchUsers(q: String) {
        userQuery = q
        fetchUsers(reset = true)
    }

    fun fetchUsers(reset: Boolean = false) {
        val current = (users as? Load.Ok)?.data
        val offset = if (reset || current == null) 0 else current.users.size
        jobs["users"]?.cancel()
        jobs["users"] = viewModelScope.launch {
            if (offset == 0) users = Load.Loading
            val term = userQuery.trim()
            val escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            val like = quoteLiteral("%$escaped%")
            val where = if (term.isNotEmpty()) "where (email ilike $like or phone ilike $like)" else ""
            val query = "select id, email, phone, created_at, last_sign_in_at, (email_confirmed_at is not null) as confirmed, banned_until, coalesce(raw_app_meta_data->>'provider', '') as provider, raw_user_meta_data as metadata from auth.users $where order by created_at desc limit ${PAGE + 1} offset $offset"
            val res = guarded {
                val rows = api.sql(query)
                UsersPage(rows.take(PAGE).map { it.toUser() }, rows.size > PAGE)
            }
            users = when {
                offset > 0 && current != null && res is Load.Ok ->
                    Load.Ok(UsersPage(current.users + res.data.users, res.data.hasMore))
                offset > 0 && current != null && res is Load.Err -> Load.Ok(current)
                else -> res
            }
        }
    }

    fun setBan(id: String, ban: Boolean) {
        viewModelScope.launch {
            val idLit = quoteLiteral(id)
            val value = if (ban) "(now() + interval '100 years')" else "null"
            val res = guarded { api.sql("update auth.users set banned_until = $value where id = $idLit", readOnly = false) }
            if (res is Load.Err) toast(res.message) else fetchUsers(reset = true)
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

    // ----- logs -----
    fun loadLogs(force: Boolean = false) = launchOnce("logs", force, logs is Load.Ok) {
        if (force) logs = Load.Loading
        val source = logSource.takeIf { it in LOG_SOURCES } ?: "edge_logs"
        logs = guarded { api.logs(source, 50) }
    }

    fun selectLogSource(source: String) {
        logSource = source
        loadLogs(force = true)
    }

    private companion object {
        const val PAGE = 50
    }
}
