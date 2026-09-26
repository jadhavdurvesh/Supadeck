package com.kestrane.neondeck.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Keystore-backed encrypted storage for the Neon connection string. There is no proxy and no
 * separate token: the connection string itself (postgresql://user:pass@host/db) is the only
 * credential, exactly as you'd hand to any Postgres client.
 */
class Store(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "neondeck_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var connectionString: String
        get() = prefs.getString("conn", "").orEmpty()
        set(v) = prefs.edit().putString("conn", v).apply()

    val isSignedIn: Boolean get() = connectionString.isNotBlank()

    fun clear() = prefs.edit().remove("conn").apply()
}
