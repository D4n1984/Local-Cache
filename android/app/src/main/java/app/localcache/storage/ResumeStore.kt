package app.localcache.storage

import android.content.Context
import app.localcache.LocalCacheApp
import java.security.MessageDigest

/** Signed download URLs and HTTP validators stay in private app data, not on the USB. */
object ResumeStore {
    private fun prefs() = LocalCacheApp.instance.getSharedPreferences("resume_secrets", Context.MODE_PRIVATE)
    private fun key(cacheKey: String) = MessageDigest.getInstance("SHA-256")
        .digest(cacheKey.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun saveUrl(cacheKey: String, url: String) {
        if (!prefs().edit().putString("url_${key(cacheKey)}", url).commit()) {
            throw java.io.IOException("Could not persist private resume URL")
        }
    }
    fun url(cacheKey: String): String = prefs().getString("url_${key(cacheKey)}", "").orEmpty()
    fun validator(cacheKey: String): String? = prefs().getString("validator_${key(cacheKey)}", null)
    fun saveValidator(cacheKey: String, validator: String?) {
        if (!prefs().edit().putString("validator_${key(cacheKey)}", validator).commit()) {
            throw java.io.IOException("Could not persist private HTTP validator")
        }
    }
    fun delete(cacheKey: String) {
        prefs().edit().remove("url_${key(cacheKey)}").remove("validator_${key(cacheKey)}").apply()
    }
}
