package com.kestrane.supadeck.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(message: String, val code: Int) : Exception(message)

/**
 * Talks directly to the Supabase Management API (api.supabase.com) using a personal access
 * token. No backend of ours sits in between — the token itself carries full account privileges,
 * the same as the SQL Editor in the Supabase dashboard.
 */
class Api(private val store: Store) {
    private val http = OkHttpClient.Builder()
        .callTimeout(40, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json".toMediaType()

    val isSignedIn: Boolean get() = store.isSignedIn

    fun connect(projectUrl: String, token: String) {
        val base = projectUrl.trim().trimEnd('/')
        require(base.startsWith("https://")) { "Project URL must start with https://" }
        require(token.trim().isNotBlank()) { "Personal access token is required" }
        store.projectUrl = base
        store.accessToken = token.trim()
    }

    fun signOut() = store.clear()

    /**
     * Runs one SQL statement via the Management API's database query endpoint and returns the
     * result rows. Defaults to read-only (the same guarantee the SQL tab always had) — pass
     * readOnly = false only for the one write action the app performs (ban/unban).
     */
    suspend fun sql(query: String, readOnly: Boolean = true): List<JSONObject> = withContext(Dispatchers.IO) {
        val ref = store.projectRef
        val url = "https://api.supabase.com/v1/projects/$ref/database/query"
        val body = JSONObject().put("query", query).put("read_only", readOnly)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${store.accessToken}")
            .post(body.toString().toRequestBody(jsonType))
            .build()
        http.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException(errorMessage(text, r.code), r.code)
            parseRows(text)
        }
    }

    /** Recent log lines for one Supabase log source, via the Management API's analytics endpoint. */
    suspend fun logs(source: String, limit: Int): LogsResult = withContext(Dispatchers.IO) {
        val ref = store.projectRef
        val end = System.currentTimeMillis()
        val start = end - 60 * 60 * 1000
        val query = "select id, timestamp, event_message from $source order by timestamp desc limit $limit"
        val url = "https://api.supabase.com/v1/projects/$ref/analytics/endpoints/logs.all"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("sql", query)
            .addQueryParameter("iso_timestamp_start", isoUtc(start))
            .addQueryParameter("iso_timestamp_end", isoUtc(end))
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${store.accessToken}")
            .get()
            .build()
        http.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) return@withContext LogsResult(true, emptyList(), errorMessage(text, r.code))
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return@withContext LogsResult(true, emptyList(), "Unexpected response")
            val rows = json.optJSONArray("result").mapObjects {
                val ts = it.optLong("timestamp", 0L)
                LogLine(time = isoUtc(ts / 1000), message = it.str("event_message"))
            }
            LogsResult(true, rows, null)
        }
    }

    private fun parseRows(text: String): List<JSONObject> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        // The endpoint returns a plain JSON array of row objects. Be defensive in case it's
        // ever wrapped in a { "result": [...] } / { "data": [...] } envelope instead.
        val direct = runCatching { JSONArray(trimmed) }.getOrNull()
        if (direct != null) return direct.mapObjects { it }
        val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return emptyList()
        for (key in listOf("result", "data", "rows")) {
            obj.optJSONArray(key)?.let { return it.mapObjects { row -> row } }
        }
        return emptyList()
    }

    private fun errorMessage(body: String, code: Int): String {
        val json = runCatching { JSONObject(body) }.getOrNull()
        val msg = json?.let { j ->
            listOf("message", "error", "msg", "error_description")
                .map { j.optString(it) }
                .firstOrNull { it.isNotBlank() }
        }
        return msg ?: "HTTP $code"
    }
}

private fun isoUtc(epochMillis: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return sdf.format(java.util.Date(epochMillis))
}
