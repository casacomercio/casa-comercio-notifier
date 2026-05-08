package com.casacomercio.notifier

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service "fantasma" cuya unica funcion es mantener el proceso vivo.
 * Sin esto, Android (especialmente Samsung One UI) puede matar el proceso aunque
 * tenga un NotificationListenerService activo, perdiendo notis.
 */
class ForwarderService : Service() {

    companion object {
        private const val CHANNEL_ID = "ccn_fg_channel"
        private const val NOTIF_ID = 1042

        fun start(ctx: Context) {
            val intent = Intent(ctx, ForwarderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: si el sistema mata el servicio, lo recrea
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotif(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.fg_notif_title))
            .setContentText(getString(R.string.fg_notif_text))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pi)
            .build()
    }
}
