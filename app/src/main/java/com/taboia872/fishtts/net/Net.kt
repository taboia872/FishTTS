package com.taboia872.fishtts.net

import java.net.HttpURLConnection
import java.net.URL

/**
 * Single network policy point for ALL outbound connections.
 *
 * Generic UA: "Mozilla/5.0" — the bare gecko-compat token every browser
 * sends as its first fragment. It carries NO device model, OS version,
 * locale or library fingerprint, minimizing data collection. It is also
 * required in practice: some API frontends 403-block the default Dalvik
 * UA that HttpURLConnection would otherwise send.
 */
object Net {
    const val GENERIC_UA = "Mozilla/5.0"

    /** Open a connection with the app-wide policy applied (UA only, for now). */
    fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", GENERIC_UA)
        }
}
