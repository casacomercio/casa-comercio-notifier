package com.casacomercio.notifier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Recibe los wake-ups del AlarmManager y rebota el evento al ForwarderService.
 * Función: si el servicio murió por Samsung killer / OOM, esto lo revive cada 10 min.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("CCN-Alarm", "Alarm wakeup recibido (action=${intent.action})")
        val svcIntent = Intent(context, ForwarderService::class.java).apply {
            action = intent.action
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(svcIntent)
            } else {
                context.startService(svcIntent)
            }
        } catch (e: Exception) {
            Log.e("CCN-Alarm", "No pude iniciar service: ${e.message}")
        }
    }
}
