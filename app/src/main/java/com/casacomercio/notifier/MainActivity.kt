package com.casacomercio.notifier

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.casacomercio.notifier.databinding.ActivityMainBinding
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.etEndpoint.setText(Prefs.getEndpoint(this))

        b.btnSave.setOnClickListener {
            val v = b.etEndpoint.text?.toString()?.trim().orEmpty()
            if (v.isBlank() || !(v.startsWith("http://") || v.startsWith("https://"))) {
                Toast.makeText(this, "URL inválida (debe empezar con http:// o https://)", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            Prefs.setEndpoint(this, v)
            Toast.makeText(this, "Guardado ✓", Toast.LENGTH_SHORT).show()
        }

        b.btnListener.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        b.btnBattery.setOnClickListener {
            try {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    !pm.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Ya está exenta de optimización de batería ✓", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        b.btnTest.setOnClickListener {
            it.isEnabled = false
            lifecycleScope.launch {
                val (ok, info) = withContext(Dispatchers.IO) {
                    HttpForwarder.forward(
                        applicationContext,
                        "Recibiste una transferencia 💸",
                        "Recibiste 1,00 ARS de TEST CCNotifier",
                        "com.applemoncash"
                    )
                }
                val msg = if (ok) "Conexión OK · $info" else "Falló · $info"
                Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
                Prefs.appendLog(
                    applicationContext,
                    Prefs.LogEntry(
                        ts = System.currentTimeMillis(),
                        titulo = "Prueba manual",
                        texto = "Probar conexión con el validador",
                        paquete = "test",
                        ok = ok,
                        info = info,
                    )
                )
                refreshLog()
                it.isEnabled = true
            }
        }

        b.btnClearLog.setOnClickListener {
            Prefs.clearLog(this)
            refreshLog()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshLog()
        if (isListenerEnabled()) ForwarderService.start(this)
        // Asegurar que tenemos token FCM y mandarlo al server
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            if (!token.isNullOrBlank()) {
                Prefs.setFcmToken(applicationContext, token)
                lifecycleScope.launch(Dispatchers.IO) {
                    HttpForwarder.registrarFcmToken(applicationContext, token)
                }
            }
        }
        // Auto-refresh suave del log mientras la pantalla esté abierta
        lifecycleScope.launch {
            while (true) {
                delay(3000)
                if (isFinishing || isDestroyed) return@launch
                refreshLog()
            }
        }
    }

    private fun refreshStatus() {
        val active = isListenerEnabled()
        if (active) {
            b.tvStatus.text = getString(R.string.status_active)
            b.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.casa_positive))
            b.chipStatus.background = ContextCompat.getDrawable(this, R.drawable.bg_chip_active)
            b.chipDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.casa_positive)
            b.tvStatusHint.text = "Escuchando notificaciones de Lemon. Cada transferencia se reenvía al validador en tiempo real."
        } else {
            b.tvStatus.text = "Sin permiso"
            b.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.casa_negative))
            b.chipStatus.background = ContextCompat.getDrawable(this, R.drawable.bg_chip_inactive)
            b.chipDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.casa_negative)
            b.tvStatusHint.text = "Otorgá el acceso de notificaciones para que la app empiece a reenviar las transferencias de Lemon."
        }
    }

    private fun isListenerEnabled(): Boolean {
        val cn = ComponentName(this, LemonNotificationListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(":").any { it.contains(cn.flattenToString()) || it.contains(cn.packageName) }
    }

    private fun refreshLog() {
        val entries = Prefs.getLog(this)
        b.logContainer.removeAllViews()

        if (entries.isEmpty()) {
            b.tvLogEmpty.visibility = View.VISIBLE
            return
        }
        b.tvLogEmpty.visibility = View.GONE

        val inflater = LayoutInflater.from(this)
        for (e in entries.take(30)) {
            val row = inflater.inflate(R.layout.item_log_entry, b.logContainer, false)
            val ivStatus = row.findViewById<ImageView>(R.id.ivStatus)
            val tvText = row.findViewById<TextView>(R.id.tvLogText)
            val tvMeta = row.findViewById<TextView>(R.id.tvLogMeta)

            ivStatus.setImageResource(if (e.ok) R.drawable.ic_check_circle else R.drawable.ic_error)
            tvText.text = if (e.texto.isNotBlank()) e.texto else e.titulo
            val ts = DateFormat.format("dd/MM HH:mm:ss", Date(e.ts)).toString()
            val infoSuffix = if (!e.ok && e.info.isNotBlank()) " · ${e.info}" else ""
            tvMeta.text = "$ts$infoSuffix"

            b.logContainer.addView(row)
        }
    }
}
