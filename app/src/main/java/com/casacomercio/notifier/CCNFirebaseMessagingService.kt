package com.casacomercio.notifier

import android.os.PowerManager
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Recibe push silenciosos del server. Cualquier mensaje despierta el ForwarderService
 * y dispara un heartbeat inmediato — esto es el wake-up que sobrevive a Samsung killers
 * (FCM messages atraviesan Doze y Sleeping apps porque Google los entrega aunque
 * el proceso este muerto).
 */
class CCNFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        Log.i(TAG, "Push recibido: data=${message.data} from=${message.from}")
        // Despertar el FG service (lo crea si no esta corriendo)
        ForwarderService.start(applicationContext)
        // Y mandar heartbeat inmediato
        val pm = applicationContext.getSystemService(POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CCN:fcm")
        wl.acquire(20_000)
        scope.launch {
            try {
                val (ok, info) = HttpForwarder.heartbeat(applicationContext)
                Log.i(TAG, "FCM-triggered heartbeat: ok=$ok, $info")
            } finally {
                try { if (wl.isHeld) wl.release() } catch (_: Exception) {}
            }
        }
    }

    override fun onNewToken(token: String) {
        Log.i(TAG, "Nuevo FCM token: ${token.take(20)}...")
        // Persistir y mandar al server
        Prefs.setFcmToken(applicationContext, token)
        scope.launch { HttpForwarder.registrarFcmToken(applicationContext, token) }
    }

    companion object {
        private const val TAG = "CCN-FCM"
    }
}
