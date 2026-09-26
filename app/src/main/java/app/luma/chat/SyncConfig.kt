package app.luma.chat

import android.content.Context

internal object SyncConfig {
    const val PUBLIC_URL = "https://174.174.23.247:8443"
    const val LAN_URL = "https://10.0.0.189:8443"
    const val TOKEN = "fd32a1f0cd0700542e61e6e8913d510e0a3533080c7aad7333592499472699fc"
    const val DEFAULT_PORT = 8443
}

/**
 * Where this phone looks for the PC.
 *
 * The built-in addresses are the home IPs as they were when the app was built.
 * A home connection usually hands out a new public address every so often, and
 * when that happens the built-in one stops working — so the address can be
 * typed in without rebuilding the app. It is kept per-device rather than in
 * [Preferences]: it describes how to reach the PC from here, and has no
 * business travelling to other devices through the synced state.
 */
internal class SyncAddress(context: Context) {
    private val prefs = context.getSharedPreferences("luma_sync", Context.MODE_PRIVATE)

    var custom: String
        get() = prefs.getString("url", "").orEmpty()
        set(value) { prefs.edit().putString("url", normalise(value)).apply() }

    /** The typed address first, then the ones built in, so a correction wins immediately. */
    fun urls(): List<String> =
        (listOf(custom, SyncConfig.PUBLIC_URL, SyncConfig.LAN_URL)).filter { it.isNotBlank() }.distinct()

    companion object {
        /** Takes "1.2.3.4", "1.2.3.4:9000", "pc.example.com" or a full URL. */
        fun normalise(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return ""
            val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            val hostAndPort = withScheme.substringAfter("://")
            return if (hostAndPort.substringAfter(':', "").toIntOrNull() != null) withScheme
            else "$withScheme:${SyncConfig.DEFAULT_PORT}"
        }
    }
}
