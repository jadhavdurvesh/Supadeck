package com.kestrane.supadeck.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Keystore-backed encrypted storage for the project URL, the public (anon/publishable) key
 * and the session tokens. No service-role key or management token is ever stored here.
 */
class Store(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "supadeck_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var projectUrl: String
        get() = prefs.getString("url", "").orEmpty()
        set(v) = prefs.edit().putString("url", v).apply()

    var anonKey: String
        get() = prefs.getString("anon", "").orEmpty()
        set(v) = prefs.edit().putString("anon", v).apply()

    var accessToken: String
        get() = prefs.getString("access", "").orEmpty()
        set(v) = prefs.edit().putString("access", v).apply()

    var refreshToken: String
        get() = prefs.getString("refresh", "").orEmpty()
        set(v) = prefs.edit().putString("refresh", v).apply()

    /** Access-token expiry, epoch seconds. */
    var expiresAt: Long
        get() = prefs.getLong("exp", 0L)
        set(v) = prefs.edit().putLong("exp", v).apply()

    fun clearSession() {
        prefs.edit().remove("access").remove("refresh").remove("exp").apply()
    }
}
