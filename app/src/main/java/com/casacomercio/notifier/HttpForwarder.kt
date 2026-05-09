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
        return doGet("$base$PATH?$q")
    }

    /**
     * Pingea el server para que sepa que la app sigue viva.
     * Si pasan >15 min sin heartbeat, el server alerta a Juan por WA y manda
     * un FCM push para despertar a la app.
     */
    fun heartbeat(ctx: Context): Pair<Boolean, String> {
        val base = Prefs.getEndpoint(ctx).trimEnd('/')
        // Aprovecho el heartbeat para reportar el FCM token (idempotente)
        val token = Prefs.getFcmToken(ctx)
        val tokenParam = if (!token.isNullOrBlank()) "&fcm_token=${enc(token)}" else ""
        val url = "$base/api/heartbeat-notifier?ts=${System.currentTimeMillis()}$tokenParam"
        return doGet(url)
    }

    /**
     * Registra/actualiza el token FCM en el server. Llamado cuando Firebase
     * emite un nuevo token (instalacion fresca o rotacion).
     */
    fun registrarFcmToken(ctx: Context, token: String): Pair<Boolean, String> {
        val base = Prefs.getEndpoint(ctx).trimEnd('/')
        val url = "$base/api/fcm-token?token=${enc(token)}"
        return doGet(url)
    }

    private fun doGet(url: String): Pair<Boolean, String> {
        return try {
            val u = URL(url)
            val conn = (u.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", "CCNotifier/1.1")
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

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
