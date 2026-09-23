package com.kestrane.supadeck.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kestrane.supadeck.data.Api
import com.kestrane.supadeck.data.ColumnInfo
import com.kestrane.supadeck.data.Grid
import com.kestrane.supadeck.data.Load
import com.kestrane.supadeck.data.LogLine
import com.kestrane.supadeck.data.LogsResult
import com.kestrane.supadeck.data.Overview
import com.kestrane.supadeck.data.SessionExpired
import com.kestrane.supadeck.data.Store
import com.kestrane.supadeck.data.TableInfo
import com.kestrane.supadeck.data.UsersPage
import com.kestrane.supadeck.data.mapObjects
import com.kestrane.supadeck.data.str
import com.kestrane.supadeck.data.toColumn
import com.kestrane.supadeck.data.toGrid
import com.kestrane.supadeck.data.toOverview
import com.kestrane.supadeck.data.toTable
import com.kestrane.supadeck.data.toUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject

class AdminViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val api = Api(store)
    private val jobs = mutableMapOf<String, Job>()

    // ----- session -----
    var signedIn by mutableStateOf(api.isSignedIn); private set
    var loginBusy by mutableStateOf(false); private set
    var loginError by mutableStateOf<String?>(null); private set
    val savedUrl: String get() = store.projectUrl
    val savedKey: String get() = store.anonKey

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

    // ----- helpers -----
    private suspend fun <T> guarded(block: suspend () -> T): Load<T> = try {
        Load.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: SessionExpired) {
        signedIn = false
        Load.Err("Session expired. Sign in again.")
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

    // ----- auth -----
    fun signIn(url: String, key: String, email: String, password: String) {
        viewModelScope.launch {
            loginBusy = true
            loginError = null
            try {
                api.signIn(url, key, email, password)
                resetData()
                signedIn = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loginError = e.message ?: "Sign-in failed"
            }
            loginBusy = false
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
        overview = guarded { api.call("overview").toOverview() }
    }

    // ----- tables -----
    fun loadTables(force: Boolean = false) = launchOnce("tables", force, tables is Load.Ok) {
        if (force) tables = Load.Loading
        tables = guarded { api.call("tables").getJSONArray("tables").mapObjects { it.toTable() } }
    }

    fun openTable(t: TableInfo) {
        selected = t
        rows = Load.Loading
        columns = Load.Loading
        loadRows(reset = true)
        viewModelScope.launch {
            columns = guarded {
                api.call("columns", JSONObject().put("schema", t.schema).put("table", t.name))
                    .getJSONArray("columns").mapObjects { it.toColumn() }
            }
        }
    }

    fun closeTable() {
        selected = null
    }

    fun loadRows(reset: Boolean = false) {
        val t = selected ?: return
        val current = (rows as? Load.Ok)?.data
        val offset = if (reset || current == null) 0 else current.rows.size
        jobs["rows"]?.cancel()
        jobs["rows"] = viewModelScope.launch {
            if (offset > 0) loadingMore = true else rows = Load.Loading
            val res = guarded {
                api.call(
                    "rows",
                    JSONObject().put("schema", t.schema).put("table", t.name).put("limit", PAGE).put("offset", offset),
                ).toGrid()
            }
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
            val res = guarded {
                val r = api.call("users", JSONObject().put("q", userQuery).put("limit", PAGE).put("offset", offset))
                UsersPage(r.getJSONArray("users").mapObjects { it.toUser() }, r.optBoolean("has_more"))
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
            val res = guarded { api.call("user_ban", JSONObject().put("id", id).put("ban", ban)) }
            if (res is Load.Err) toast(res.message) else fetchUsers(reset = true)
        }
    }

    // ----- SQL -----
    fun runSql(query: String) {
        jobs["sql"]?.cancel()
        jobs["sql"] = viewModelScope.launch {
            sqlResult = Load.Loading
            sqlResult = guarded { api.call("sql", JSONObject().put("query", query)).toGrid() }
        }
    }

    // ----- logs -----
    fun loadLogs(force: Boolean = false) = launchOnce("logs", force, logs is Load.Ok) {
        if (force) logs = Load.Loading
        val source = logSource
        logs = guarded {
            val r = api.call("logs", JSONObject().put("source", source).put("limit", 50))
            LogsResult(
                configured = r.optBoolean("configured", true),
                lines = r.getJSONArray("rows").mapObjects { LogLine(it.str("time"), it.str("message")) },
                error = if (r.isNull("error")) null else r.optString("error"),
            )
        }
    }

    fun selectLogSource(source: String) {
        logSource = source
        loadLogs(force = true)
    }

    private companion object {
        const val PAGE = 50
    }
}
