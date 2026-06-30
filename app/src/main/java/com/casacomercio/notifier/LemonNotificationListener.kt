package com.casacomercio.notifier

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LemonNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "CCN-Listener"
        private val PAQUETES_OK = setOf(
            "com.applemoncash",        // Lemon Cash
            "com.brubank",             // Brubank
        )
        // Detector de líneas con transferencia real (para discriminar dentro de notis
        // agrupadas / Inbox style). Cubre dos formatos:
        //   Lemon:   "Recibiste 32.130 ARS" / "MERCADO LUCAS te envió 62.300 ARS"
        //   Brubank: "QUINTANAL,SILVIA ALICI te envió $ 37.800"
        private val RX_TRANSFER = Regex(
            "(Recibiste|te\\s+envi[oó]|te\\s+transfiri[oó])\\s.*?(\\$\\s*[\\d.,]+|[\\d.,]+\\s*ARS)",
            RegexOption.IGNORE_CASE
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Listener conectado")
        ForwarderService.start(applicationContext)

        // Al conectar, reprocesar las notis activas. Si Lemon dejó una transferencia
        // pinned y no la habíamos visto, la procesamos ahora.
        try {
            val activas = activeNotifications ?: emptyArray()
            Log.i(TAG, "${activas.size} notificaciones activas al conectar")
            for (sbn in activas) {
                if (sbn.packageName in PAQUETES_OK) procesar(sbn, fuente = "rebind")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando activas: ${e.message}")
        }
    }

    override fun onListenerDisconnected() {
        Log.w(TAG, "Listener desconectado, pidiendo rebind")
        super.onListenerDisconnected()
        try {
            requestRebind(ComponentName(this, LemonNotificationListener::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "requestRebind falló: ${e.message}")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        if (pkg !in PAQUETES_OK) return
        procesar(sbn, fuente = "posted")
    }

    /**
     * Procesa una notificación. Maneja tres casos:
     *  - Noti individual estándar: titulo + texto
     *  - Noti agrupada con EXTRA_TEXT_LINES (Inbox style): cada línea = una transferencia
     *  - Summary de un grupo (FLAG_GROUP_SUMMARY): la skipeamos, las hijas vienen aparte
     */
    private fun procesar(sbn: StatusBarNotification, fuente: String) {
        val n: Notification = sbn.notification ?: return
        val extras = n.extras ?: return
        val pkg = sbn.packageName

        val esSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val titulo = (extras.getCharSequence(Notification.EXTRA_TITLE) ?: "").toString()
        val texto = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: "").toString()
        val bigText = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: "").toString()
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() } ?: emptyList()

        Log.i(TAG, "[$fuente] pkg=$pkg summary=$esSummary titulo='$titulo' texto='$texto' bigText='${bigText.take(80)}' lines=${textLines.size}")

        // Caso 1: noti agrupada con líneas individuales (Inbox style)
        if (textLines.isNotEmpty()) {
            Log.i(TAG, "Procesando ${textLines.size} líneas de noti agrupada")
            for (linea in textLines) {
                if (RX_TRANSFER.containsMatchIn(linea)) {
                    enviar(titulo.ifBlank { "Recibiste una transferencia 💸" }, linea, pkg)
                }
            }
            return
        }

        // Caso 2: bigText con varias líneas (algunas variantes de notis agrupadas)
        if (bigText.contains("\n") && RX_TRANSFER.containsMatchIn(bigText)) {
            Log.i(TAG, "Procesando bigText multilínea")
            for (linea in bigText.split("\n")) {
                if (RX_TRANSFER.containsMatchIn(linea)) {
                    enviar(titulo.ifBlank { "Recibiste una transferencia 💸" }, linea.trim(), pkg)
                }
            }
            return
        }

        // Caso 3: summary "X new messages" sin líneas — no podemos extraer info, skipeamos
        if (esSummary && !RX_TRANSFER.containsMatchIn(texto) && !RX_TRANSFER.containsMatchIn(bigText)) {
            Log.i(TAG, "Summary sin transferencia individual — skip")
            return
        }

        // Caso 4: noti individual estándar
        val textoFinal = if (texto.isNotBlank()) texto else bigText
        enviar(titulo, textoFinal, pkg)
    }

    private fun enviar(titulo: String, texto: String, pkg: String) {
        Log.i(TAG, "Enviando: titulo='$titulo' texto='$texto'")
        val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CCN:notif")
        wl.acquire(30_000)
        scope.launch {
            try {
                val (ok, info) = HttpForwarder.forward(applicationContext, titulo, texto, pkg)
                Prefs.appendLog(
                    applicationContext,
                    Prefs.LogEntry(
                        ts = System.currentTimeMillis(),
                        titulo = titulo,
                        texto = texto,
                        paquete = pkg,
                        ok = ok,
                        info = info,
                    )
                )
            } finally {
                try { if (wl.isHeld) wl.release() } catch (_: Exception) {}
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
