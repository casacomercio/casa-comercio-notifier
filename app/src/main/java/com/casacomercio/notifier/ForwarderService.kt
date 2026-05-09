package com.casacomercio.notifier

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service que mantiene viva la app contra Doze y los killers de Samsung One UI.
 *
 * Mecanismos de keepalive:
 *  1. Foreground notification persistente
 *  2. Heartbeat HTTP al server cada 5 min (con WakeLock)
 *  3. AlarmManager setAndAllowWhileIdle cada 10 min que se reschedulea solo (sobrevive a Doze)
 *  4. START_STICKY: si el sistema mata el servicio, lo recrea
 */
class ForwarderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null

    companion object {
        private const val TAG = "CCN-Service"
        private const val CHANNEL_ID = "ccn_fg_channel"
        private const val NOTIF_ID = 1042
        private const val HEARTBEAT_INTERVAL_MS = 5 * 60 * 1000L  // 5 min
        private const val ALARM_INTERVAL_MS = 10 * 60 * 1000L     // 10 min
        private const val ACTION_ALARM = "com.casacomercio.notifier.ACTION_ALARM"
        private const val ALARM_REQUEST_CODE = 4242

        fun start(ctx: Context) {
            val intent = Intent(ctx, ForwarderService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(intent)
                } else {
                    ctx.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando service: ${e.message}")
            }
        }

        fun scheduleNextAlarm(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(ctx, AlarmReceiver::class.java).apply { action = ACTION_ALARM }
            val pi = PendingIntent.getBroadcast(
                ctx, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = SystemClock.elapsedRealtime() + ALARM_INTERVAL_MS
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // setAndAllowWhileIdle no requiere SCHEDULE_EXACT_ALARM y dispara igual durante Doze
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                } else {
                    am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
                }
                Log.i(TAG, "AlarmManager programado en ${ALARM_INTERVAL_MS / 60000} min")
            } catch (e: SecurityException) {
                Log.e(TAG, "No se pudo programar alarma: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Servicio de Notificaciones",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "Mantiene el reenvio de notificaciones activo"
                    setShowBadge(false)
                }
                mgr.createNotificationChannel(ch)
            }
        }
        startForeground(NOTIF_ID, buildNotif())
        iniciarHeartbeat()
        scheduleNextAlarm(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Si vino por alarma, asegurar que el heartbeat esté corriendo y reschedulear
        if (intent?.action == ACTION_ALARM) {
            Log.i(TAG, "onStartCommand desde AlarmManager")
            if (heartbeatJob?.isActive != true) iniciarHeartbeat()
            scheduleNextAlarm(this)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun iniciarHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            // Primer heartbeat inmediato
            mandarHeartbeat()
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                mandarHeartbeat()
            }
        }
    }

    private fun mandarHeartbeat() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CCN:heartbeat")
        try {
            wl.acquire(20_000) // 20s max
            val (ok, info) = HttpForwarder.heartbeat(applicationContext)
            Log.i(TAG, "Heartbeat: ok=$ok, $info")
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat error: ${e.message}")
        } finally {
            try { if (wl.isHeld) wl.release() } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        Log.w(TAG, "onDestroy — cancelando scope")
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotif(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.fg_notif_title))
            .setContentText(getString(R.string.fg_notif_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.casa_naranja))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pi)
            .build()
    }
}
