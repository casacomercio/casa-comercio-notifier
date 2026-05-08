package com.casacomercio.notifier

import android.content.Context
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object HttpForwarder {
    private const val TAG = "HttpForwarder"
    private const val PATH = "/api/notificacion-mp"
    private const val TIMEOUT_MS = 15000

    /**
     * GET <endpoint>/api/notificacion-mp?titulo=...&texto=...&paquete=...
     * Mismo formato que estaba usando el MacroDroid asi no hay que tocar el server.
     * Retorna Pair<Boolean (ok), String (info breve para log)>.
     */
    fun forward(ctx: Context, titulo: String, texto: String, paquete: String): Pair<Boolean, String> {
        val base = Prefs.getEndpoint(ctx).trimEnd('/')
        val q = buildString {
            append("titulo=").append(enc(titulo))
            append("&texto=").append(enc(texto))
            append("&paquete=").append(enc(paquete))
        }
        val url = "$base$PATH?$q"

        return try {
            val u = URL(url)
            val conn = (u.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", "CCNotifier/1.0")
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                Log.i(TAG, "HTTP $code → $body")
                val ok = code in 200..299
                val info = "HTTP $code" + if (body.length in 1..120) " — ${body.replace("\n", " ")}" else ""
                ok to info
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            false to "Error: ${e.message}"
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s ?: "", "UTF-8")
}
