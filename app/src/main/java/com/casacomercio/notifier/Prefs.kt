package com.casacomercio.notifier

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object Prefs {
    private const val FILE = "ccn_prefs"
    private const val K_ENDPOINT = "endpoint"
    private const val K_LOG = "log_json"
    private const val DEFAULT_ENDPOINT = "http://76.13.162.145:3001"
    private const val MAX_LOG_ENTRIES = 50

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getEndpoint(ctx: Context): String =
        sp(ctx).getString(K_ENDPOINT, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT

    fun setEndpoint(ctx: Context, value: String) {
        sp(ctx).edit().putString(K_ENDPOINT, value.trim().trimEnd('/')).apply()
    }

    data class LogEntry(
        val ts: Long,
        val titulo: String,
        val texto: String,
        val paquete: String,
        val ok: Boolean,
        val info: String,
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("ts", ts)
            put("titulo", titulo)
            put("texto", texto)
            put("paquete", paquete)
            put("ok", ok)
            put("info", info)
        }

        companion object {
            fun fromJson(o: JSONObject) = LogEntry(
                ts = o.optLong("ts"),
                titulo = o.optString("titulo"),
                texto = o.optString("texto"),
                paquete = o.optString("paquete"),
                ok = o.optBoolean("ok"),
                info = o.optString("info"),
            )
        }
    }

    fun getLog(ctx: Context): List<LogEntry> {
        val raw = sp(ctx).getString(K_LOG, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { LogEntry.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun appendLog(ctx: Context, entry: LogEntry) {
        synchronized(this) {
            val current = getLog(ctx).toMutableList()
            current.add(0, entry)
            while (current.size > MAX_LOG_ENTRIES) current.removeAt(current.size - 1)
            val arr = JSONArray()
            current.forEach { arr.put(it.toJson()) }
            sp(ctx).edit().putString(K_LOG, arr.toString()).apply()
        }
    }

    fun clearLog(ctx: Context) {
        sp(ctx).edit().putString(K_LOG, "[]").apply()
    }
}
