package com.casacomercio.notifier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("BootReceiver", "Boot received: ${intent.action}, arrancando ForwarderService")
        ForwarderService.start(context)
    }
}
