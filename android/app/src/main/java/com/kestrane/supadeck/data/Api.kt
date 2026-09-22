package com.kestrane.supadeck.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(message: String, val code: Int) : Exception(message)
class SessionExpired : Exception("Session expired")

/**
 * Talks to exactly two things: Supabase Auth (sign in / refresh) and your `admin-api` Edge Function.
 */
class Api(private val store: Store) {
    private val http = OkHttpClient.Builder().callTimeout(40, TimeUnit.SECONDS).build()
    private val jsonType = "application/json".toMediaType()
    private val refreshLock = Mutex()

    val isSignedIn: Boolean
        get() = store.refreshToken.isNotBlank() && store.projectUrl.isNotBlank()

    suspend fun signIn(url: String, anonKey: String, email: String, password: String) {
        val base = url.trim().trimEnd('/')
        val key = anonKey.trim()
        require(base.startsWith("https://")) { "Project URL must start with https://" }
        val res = send(
            "$base/auth/v1/token?grant_type=password",
            JSONObject().put("email", email.trim()).put("password", password),
            key,
            null,
        )
        saveSession(base, key, res)
    }

    fun signOut() = store.clearSession()

    /** Calls one action on the admin-api function, refreshing the session when needed. */
    suspend fun call(action: String, params: JSONObject = JSONObject()): JSONObject {
        if (System.currentTimeMillis() / 1000 > store.expiresAt - 60) refresh(store.accessToken)
        val body = JSONObject(params.toString()).put("action", action)
        val used = store.accessToken
        return try {
            invoke(body)
        } catch (e: ApiException) {
            if (e.code != 401) throw e
            refresh(used)
            invoke(body)
        }
    }

    private suspend fun invoke(body: JSONObject): JSONObject =
        send("${store.projectUrl}/functions/v1/admin-api", body, store.anonKey, store.accessToken)

    private suspend fun refresh(stale: String) = refreshLock.withLock {
        if (store.accessToken != stale) return@withLock // another call already refreshed
        try {
            val res = send(
                "${store.projectUrl}/auth/v1/token?grant_type=refresh_token",
                JSONObject().put("refresh_token", store.refreshToken),
                store.anonKey,
                null,
            )
            saveSession(store.projectUrl, store.anonKey, res)
        } catch (e: ApiException) {
            if (e.code in 400..499) {
                store.clearSession()
                throw SessionExpired()
            }
            throw e
        }
    }

    private fun saveSession(url: String, anonKey: String, res: JSONObject) {
        store.projectUrl = url
        store.anonKey = anonKey
        store.accessToken = res.getString("access_token")
        store.refreshToken = res.getString("refresh_token")
        store.expiresAt = res.optLong("expires_at", System.currentTimeMillis() / 1000 + res.optLong("expires_in", 3600))
    }

    private suspend fun send(url: String, body: JSONObject, apiKey: String, bearer: String?): JSONObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("apikey", apiKey)
                .also { if (bearer != null) it.header("Authorization", "Bearer $bearer") }
                .post(body.toString().toRequestBody(jsonType))
                .build()
            http.newCall(request).execute().use { r ->
                val text = r.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (!r.isSuccessful) {
                    val msg = json?.let { j ->
                        listOf("error_description", "msg", "message", "error")
                            .map { j.optString(it) }
                            .firstOrNull { it.isNotBlank() }
                    }
                    throw ApiException(msg ?: "HTTP ${r.code}", r.code)
                }
                json ?: JSONObject()
            }
        }
}
