package app.localcache.server

import android.content.Context
import app.localcache.BuildConfig
import java.security.SecureRandom

/** Random per-install control token. Never stored on USB or returned by public endpoints. */
object AdminAccess {
    @Synchronized fun token(context: Context): String {
        val prefs = context.getSharedPreferences(BuildConfig.PREFS_FILE, Context.MODE_PRIVATE)
        prefs.getString("admin_token", null)?.let { return it }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        prefs.edit().putString("admin_token", token).apply()
        return token
    }

    fun settingsUrl(context: Context, port: Int): String =
        "http://127.0.0.1:$port/settings?admin=${token(context)}"
}
