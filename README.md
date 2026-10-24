# Telemetría - App Android para Coches de Calle y Circuito

App nativa para Android (Kotlin + Jetpack Compose) de telemetría automovilística de alto rendimiento:
* **Instrumentación estilo MyChron 5**: Pantalla de contraste deportivo alto (negro OLED, barras de estado y widgets nítidos de alta visibilidad).
* **Registro estilo Strava**: Grabación continua de salidas en segundo plano, cálculo de métricas en tiempo real, mapa offline y consulta de historial sin conexión ni cuentas.
* **Exportación de Telemetría**: Exportación a GPX y CSV (marcas de tiempo duales UTC y monotónicas de nanosegundos para sincronización con RaceRender y edición en PC).
* **100% Offline & Local**: Sin dependencias de servicios de pago, sin cuentas ni nube.

---

## 🛠 Arquitectura y Tecnologías

* **Lenguaje & UI**: Kotlin, Jetpack Compose, Material 3, Clean Architecture + MVVM.
* **Base de Datos**: Room (SQLite) con transacciones por lotes (`TelemetryBatchWriter`) y modo WAL (*Write-Ahead Logging*).
* **Ubicación & GPS**: `FusedLocationProviderClient` ejecutado dentro de un Foreground Service con `WAKE_LOCK` y canal de notificación persistente.
* **Mapa**: Osmdroid (OpenStreetMap local sin API keys ni suscripciones).
* **Gráficas**: Canvas nativo de Jetpack Compose optimizado a 60/120 fps.

---

## 📁 Estructura del Código

```text
com.telemetria.app/
├── core/
│   ├── model/       # Entidades de dominio (Session, TelemetryPoint, GpsSnapshot)
│   ├── database/    # Room DB, DAOs y entidades persistentes
│   ├── location/    # Fused Location Provider, Foreground Service y helpers de batería
│   ├── session/     # SessionManager y escritor incremental por lotes
│   ├── export/      # Exporte a GPX 1.1 y CSV determinista
│   └── util/        # Cálculo geodésico Haversine y formateadores
└── ui/
    ├── theme/       # Paleta y tipografía deportivas
    ├── dashboard/   # Instrumentación central (SpeedGauge, RpmBar, StatusIndicators)
    ├── history/     # Historial de sesiones, detalle, mapa Osmdroid y gráfica Canvas
    └── navigation/  # Control de flujo y rutas con Navigation Compose
```

---

## 🚀 Compilación y Ejecución

### Requisitos
* JDK 17
* Android SDK (Plataforma 34 / Build Tools 34.0.0+)
* Dispositivo o emulador con Android 8.0+ (API 26+)

### Comandos Gradle
```bash
# Ejecutar pruebas unitarias
./gradlew testDebugUnitTest

# Compilar APK de depuración
./gradlew assembleDebug
```
El archivo ejecutable generado se sitúa en `app/build/outputs/apk/debug/app-debug.apk`.
