package com.casacomercio.notifier

import android.app.Notification
import android.content.Context
import android.content.Intent
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
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Listener conectado")
        // Arrancar foreground service para mantenernos vivos
        ForwarderService.start(applicationContext)
    }

    override fun onListenerDisconnected() {
        Log.w(TAG, "Listener desconectado, pidiendo rebind")
        super.onListenerDisconnected()
        // Pedir a Android que vuelva a bindearnos cuando se cae
        try {
            requestRebind(android.content.ComponentName(this, LemonNotificationListener::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "requestRebind falló: ${e.message}")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        if (pkg !in PAQUETES_OK) return

        val n: Notification = sbn.notification ?: return
        val extras = n.extras ?: return
        val titulo = (extras.getCharSequence(Notification.EXTRA_TITLE) ?: "").toString()
        val texto = (extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: "").toString()

        Log.i(TAG, "Noti capturada [$pkg] titulo='$titulo' texto='$texto'")

        // WakeLock breve durante el procesamiento. Garantiza que el HTTP termine
        // aunque el celu intente entrar en doze justo en este momento.
        val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CCN:notif")
        wl.acquire(30_000) // 30s max safety

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
