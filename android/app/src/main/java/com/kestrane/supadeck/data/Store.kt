package com.kestrane.supadeck.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Keystore-backed encrypted storage for the project URL and the Supabase personal access token.
 *
 * There is no backend proxy: the app talks to the Supabase Management API directly using this
 * token. That token is a master credential for the whole Supabase account, not just one
 * project's database, so it is stored encrypted and never leaves the device except in requests
 * to api.supabase.com and <project>.supabase.co.
 */
class Store(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "supadeck_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** Full project URL, e.g. https://abcd1234.supabase.co */
    var projectUrl: String
        get() = prefs.getString("url", "").orEmpty()
        set(v) = prefs.edit().putString("url", v).apply()

    /** Supabase personal access token (starts with sbp_). Full account access — treat as a master password. */
    var accessToken: String
        get() = prefs.getString("token", "").orEmpty()
        set(v) = prefs.edit().putString("token", v).apply()

    val isSignedIn: Boolean
        get() = projectUrl.isNotBlank() && accessToken.isNotBlank()

    /** The project ref is the subdomain of the project URL: https://<ref>.supabase.co */
    val projectRef: String
        get() = projectUrl.substringAfter("://").substringBefore(".")

    fun clear() {
        prefs.edit().remove("url").remove("token").apply()
    }
}
