package com.casacomercio.notifier

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.casacomercio.notifier.databinding.ActivityMainBinding
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
            val v = b.etEndpoint.text.toString().trim()
            if (v.isBlank() || !(v.startsWith("http://") || v.startsWith("https://"))) {
                Toast.makeText(this, "URL invalida (debe empezar con http:// o https://)", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            Prefs.setEndpoint(this, v)
            Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, "Ya esta exenta de optimizacion de bateria ✓", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // Si falla, abrir settings genericos de bateria
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        b.btnTest.setOnClickListener {
            b.btnTest.isEnabled = false
            lifecycleScope.launch {
                val (ok, info) = withContext(Dispatchers.IO) {
                    HttpForwarder.forward(
                        applicationContext,
                        "Recibiste una transferencia 💸",
                        "Recibiste 1,00 ARS de TEST CCNotifier",
                        "com.applemoncash"
                    )
                }
                val msg = if (ok) "✅ Conexion OK — $info" else "❌ Falló — $info"
                Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
                Prefs.appendLog(
                    applicationContext,
                    Prefs.LogEntry(
                        ts = System.currentTimeMillis(),
                        titulo = "[TEST]",
                        texto = "Probar conexion",
                        paquete = "test",
                        ok = ok,
                        info = info,
                    )
                )
                refreshLog()
                b.btnTest.isEnabled = true
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
        // Si tiene permiso, asegurarse que el FG service este corriendo
        if (isListenerEnabled()) {
            ForwarderService.start(this)
        }
        // Auto-refresh del log cada 3s mientras la pantalla este abierta
        lifecycleScope.launch {
            while (true) {
                delay(3000)
                if (!isFinishing && !isDestroyed) refreshLog() else return@launch
            }
        }
    }

    private fun refreshStatus() {
        val active = isListenerEnabled()
        b.tvStatus.text = if (active) getString(R.string.status_active) else getString(R.string.status_inactive)
        b.tvStatus.setTextColor(getColor(if (active) R.color.green_ok else R.color.red_error))
    }

    private fun isListenerEnabled(): Boolean {
        val cn = ComponentName(this, LemonNotificationListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.split(":").any { it.contains(cn.flattenToString()) || it.contains(cn.packageName) }
    }

    private fun refreshLog() {
        val entries = Prefs.getLog(this)
        if (entries.isEmpty()) {
            b.tvLog.text = "(sin envíos aún)"
            return
        }
        val sb = StringBuilder()
        for (e in entries.take(30)) {
            val ts = DateFormat.format("dd/MM HH:mm:ss", Date(e.ts))
            val mark = if (e.ok) "✓" else "✗"
            sb.append("$mark  $ts  ")
            sb.append(if (e.titulo.isNotBlank()) "${e.titulo} — " else "")
            sb.append(e.texto.take(70))
            if (!e.ok) sb.append("  [${e.info}]")
            sb.append("\n")
        }
        b.tvLog.text = sb.toString()
    }
}
