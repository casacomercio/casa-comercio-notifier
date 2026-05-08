# Casa Comercio Notifier

App Android nativa que reemplaza a MacroDroid para reenviar notificaciones de Lemon Cash al Validador de Pagos.

## Qué hace

1. Escucha notificaciones del paquete `com.applemoncash` (Lemon Cash).
2. Cuando llega una, manda un GET HTTP al validador con título + texto + paquete.
3. Foreground service para que Android no la mate.
4. Auto-arranca al bootear el celular.

## Instalación

1. Bajar el último APK de [Releases](../../releases).
2. En el celu: Ajustes → Seguridad → habilitar "Instalar apps desconocidas" para el navegador o el explorador de archivos.
3. Tocar el APK descargado → Instalar.
4. Abrir la app y:
   - Tocar **"Activar permiso de notificaciones"** → activar la app en la lista.
   - Tocar **"Quitar de optimización de batería"** → confirmar.
   - **(Samsung One UI):** Ajustes → Mantenimiento del dispositivo → Batería → Límites de uso en segundo plano → asegurarse que la app NO esté en "Sleeping apps" / "Deep sleeping apps".
   - Tocar **"Probar conexión"** → debería decir "Conexión OK".
5. Listo. Cada vez que llegue una noti de Lemon, la app la reenvía automáticamente.

## Desarrollo

- Kotlin + AndroidX
- minSDK 26 (Android 8.0), targetSDK 34
- Sin dependencias raras: solo NotificationListenerService + Foreground Service + HttpURLConnection.

## Build

```
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. CI compila automáticamente con cada push a `main` y publica en Releases.
