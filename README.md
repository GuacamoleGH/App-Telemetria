# Tachyon - Telemetría para Coches de Calle y Circuito

[![Download APK](https://img.shields.io/badge/Download-APK-brightgreen?style=for-the-badge&logo=android)](apk/Tachyon.apk)

> 📲 **Descarga directa:** Puedes descargar e instalar la aplicación lista para usar directamente desde el repositorio: [**`apk/Tachyon.apk`**](apk/Tachyon.apk).

App nativa para Android (Kotlin + Jetpack Compose) de telemetría automovilística de alto rendimiento diseñada para carretera y circuito:

- **Instrumentación estilo MyChron 5**: Pantalla de alto contraste (negro OLED, tacómetro por LEDs de color con shift light, velocímetro digital central, fuerzas G laterales y longitudinales, pedales y estado de enlace).
- **Ajustes de Tacómetro y Shift Lights de Competición**: Calibración personalizada del régimen de giro de tu vehículo (RPM máximas, inicio de línea roja / corte, y punto de activación del aviso de cambio estroboscópico tipo GT3 / F1 con badge parpadeante `⚡ SHIFT`). Se almacena en preferencias y se exporta a `session.json` para renderizar el tablero fiel a tu coche en el overlay del vídeo en PC.
- **Registro estilo Strava**: Grabación continua de salidas en segundo plano, cálculo de distancias geodésicas en tiempo real, mapa offline y consulta de historial sin conexión ni cuentas.
- **Vídeo con Telemetría Sincronizada estilo RaceRender**: Grabación de vídeo nítido (CameraX) con registro de marca de tiempo monotónica a nivel de hardware, exportación a ZIP (`video.mp4`, `telemetria.csv`, `ruta.gpx`, `session.json`) y script autónomo en Python + OpenCV para renderizar el overlay en PC sin coste de procesamiento en el móvil.
- **Integración OBD-II Bluetooth Low Energy (Veepeak OBDCheck BLE)**: Conexión BLE directa (GATT), descubrimiento dinámico de características NOTIFY y WRITE, implementación completa del protocolo ELM327 y planificador de consulta adaptativa por prioridad (RPM y acelerador a máxima frecuencia, temperaturas y carga a baja frecuencia).
- **100% Offline & Local**: Sin dependencias de servicios de pago, sin suscripciones, sin envío de datos a la nube.

---

## 🏎️ Características Principales

| Módulo                  | Descripción                                                                                                                                                                                                                                                                                                             |
| :---------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Cockpit / Dashboard** | Tacómetro visual (0-8000 RPM con zonas verde, amarilla y roja), velocímetro de gran tamaño, acelerómetro 2D (círculo de Kamm con fuerzas G longitudinal y lateral), telemetría OBD-II (pedal acelerador %, carga de motor %, temperatura de refrigerante °C).                                                           |
| **Sensores Inerciales** | Muestreo a 50 Hz de acelerómetro y giroscopio (`SensorManager`), calibración de orientación del móvil respecto a los ejes del vehículo mediante botón de tara en reposo. Distinción estricta entre fuerzas inerciales y aceleración derivada por GPS.                                                                   |
| **Cámara & Vídeo**      | Grabación de vídeo limpio en segundo plano con CameraX (`VideoCapture`), vinculación temporal monotónica (`SystemClock.elapsedRealtimeNanos()`), control de almacenamiento (mínimo 500 MB libres para seguridad).                                                                                                       |
| **OBD-II BLE**          | Soporte para adaptadores ELM327 BLE (Veepeak OBDCheck BLE). Descubrimiento dinámico de GATT (servicio `0000FFF0...` o fallback universal a NOTIFY+WRITE), parseo de tramas fragmentadas por MTU, detección automática de PIDs soportados (`0100`, `0120`, `0140`), y medición en tiempo real de Hz efectivos por canal. |
| **Exportación**         | Generador de paquetes ZIP por sesión con estructura compatible con RaceRender y software de edición de vídeo en PC.                                                                                                                                                                                                     |
| **Post-procesado PC**   | Script Python con OpenCV y FFmpeg (`pc_tools/render_telemetry_overlay.py`) para renderizar el panel MyChron 5 como overlay sobre el vídeo original con precisión de milisegundos.                                                                                                                                       |

---

## 🛠️ Arquitectura y Tecnologías

- **Lenguaje & UI**: Kotlin 1.9.22, Jetpack Compose, Material 3, Arquitectura limpia MVVM, Corrutinas y Flow reactivo.
- **Base de Datos**: Room (SQLite) con transacciones por lotes (`TelemetryBatchWriter` vía `Channel`) y modo WAL (_Write-Ahead Logging_).
- **Ubicación & GPS**: `FusedLocationProviderClient` ejecutado dentro de un Foreground Service con `FOREGROUND_SERVICE_LOCATION`, `WAKE_LOCK` y notificación persistente.
- **Sensores**: `Sensor.TYPE_LINEAR_ACCELERATION`, `TYPE_ACCELEROMETER`, `TYPE_GYROSCOPE` con matriz de proyección de ejes.
- **Bluetooth**: Android BLE API (BluetoothGatt), MTU reassembly y buffers de acumulación hasta prompt `>`.
- **Cámara**: AndroidX CameraX (Core, Camera2, Lifecycle, Video, View).
- **Mapa**: Osmdroid (OpenStreetMap local sin API keys ni suscripciones).
- **Gráficas**: Canvas nativo de Jetpack Compose optimizado a 60/120 fps.

---

## 📁 Estructura del Proyecto

```text
app/src/main/java/com/telemetria/app/
├── core/
│   ├── database/        # Room Database (v2 con soporte de vídeo), DAOs y entidades
│   ├── export/          # Exportadores GPX, CSV, JSON metadata y empaquetador ZIP
│   ├── location/        # Fused Location Provider, Foreground Service y gestión de batería
│   ├── model/           # Modelos de dominio (Session, TelemetryPoint, SessionLiveState)
│   ├── obd/             # Capa OBD-II BLE:
│   │   ├── model/       # ObdData, ObdConnectionState
│   │   ├── polling/     # ObdPollingScheduler (bucle adaptativo por prioridad y medición de Hz)
│   │   ├── protocol/    # Elm327Protocol (comandos AT, máscaras de bits de PIDs y decodificadores)
│   │   ├── scanner/     # BleScannerHelper (escaneo de dispositivos BLE en tiempo real)
│   │   ├── transport/   # ObdTransport (interfaz abstracta desacoplada del medio físico)
│   │   └── ObdManager.kt# Orquestador del ciclo de vida OBD-II
│   ├── sensor/          # SensorManager (50 Hz) y OrientationCalibrator
│   ├── session/         # SessionManager y TelemetryBatchWriter
│   ├── util/            # Cálculo geodésico Haversine y formateadores de datos
│   └── video/           # CameraX VideoRecordingManager y StorageSpaceHelper
├── ui/
│   ├── dashboard/       # Cockpit MyChron 5 (tacómetro, velocímetro, fuerzas G, PiP de cámara)
│   ├── history/         # Lista de sesiones, detalle, mapa de ruta, gráficas de velocidad y exportación ZIP
│   ├── navigation/      # Navegación entre pantallas
│   └── theme/           # Paleta de colores de alto contraste deportivo (Cockpit OLED)
pc_tools/
├── render_telemetry_overlay.py  # Script CLI en Python para renderizar telemetría sobre el vídeo
├── requirements.txt             # Dependencias (opencv-python)
└── README.md                    # Guía paso a paso de uso en PC
```

---

## 🚀 Compilación e Instalación

### Requisitos Previos

- **JDK**: Versión 17 (Java 17 LTS recomendada).
- **Android SDK**: Plataforma API 34, Build Tools 34.0.0+.
- **Dispositivo Android**: Android 8.0 (API 26) o superior con GPS y Bluetooth LE.

### 1. Compilación mediante Terminal (Gradle Wrapper)

```bash
# Ejecutar todas las pruebas unitarias
./gradlew testDebugUnitTest

# Compilar el APK de depuración
./gradlew assembleDebug
```

El archivo APK precompilado se encuentra disponible directamente en la carpeta [`apk/Tachyon.apk`](apk/Tachyon.apk).
Si compilas manualmente, se ubicará en:
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Instalación en el Dispositivo

Conecta el teléfono por USB con la depuración USB activada y ejecuta:

```bash
adb install -r apk/Tachyon.apk
```

---

## 📱 Guía de Permisos en Android

La aplicación solicita los siguientes permisos en tiempo de ejecución:

1. **Ubicación (`ACCESS_FINE_LOCATION`)**: Necesario para el registro GPS de alta precisión en carretera y circuito.
2. **Servicio en Primer Plano (`FOREGROUND_SERVICE_LOCATION`)**: Permite continuar grabando aunque la pantalla se apague o cambies de aplicación.
3. **Cámara y Micrófono (`CAMERA`, `RECORD_AUDIO`)**: Necesarios si se activa la grabación de vídeo.
4. **Bluetooth en Android 12+ (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`)**: Para escanear y comunicarse con el adaptador Veepeak OBDCheck BLE sin requerir ubicación continua en versiones modernas de Android.
5. **Exclusión de Ahorro de Batería (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)**: Crucial para evitar que sistemas como MIUI, EMUI o OneUI suspendan el servicio durante sesiones largas en circuito.

---

## 🔌 Conexión con Veepeak OBDCheck BLE

El adaptador **Veepeak OBDCheck BLE** utiliza Bluetooth Low Energy con servicios GATT estándar:

1. Conecta el adaptador en el puerto OBD-II del vehículo (generalmente bajo el volante).
2. Pon el contacto del coche o arranca el motor.
3. Abre la app Telemetría y pulsa sobre el indicador **OBD** en el panel superior.
4. Pulsa **ESCANEAR**. La app listará los dispositivos BLE cercanos con su nivel de señal (RSSI).
5. Selecciona el dispositivo (suele identificarse como `OBDCheck BLE`, `VEEPEAK` o similar).
6. La app iniciará la negociación automática:
   - Negocia MTU ampliado (hasta 256 bytes).
   - Descubre el servicio GATT (predeterminado `0000FFF0-0000-1000-8000-00805F9B34FB` con características `FFF1` notify y `FFF2` write, o fallback dinámico a cualquier pareja notify/write).
   - Inicializa el chip (`ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH0`, `ATSP0`).
   - Consulta PIDs soportados (`0100`, `0120`, `0140`).
   - Activa el bucle de sondeo adaptativo midiendo los hercios reales alcanzados.

---

## 🎬 Exportación y Sincronización de Vídeo

Cada sesión finalizada puede exportarse directamente desde la pantalla de detalle mediante el botón **EXPORTAR ZIP**.

### Contenido del Archivo ZIP:

- `video.mp4`: Vídeo original limpio grabado con la cámara del dispositivo.
- `telemetria.csv`: Archivo CSV con marcas de tiempo relativas al inicio del vídeo (`time_offset_video_sec`, siendo `0.000` el instante exacto en que arrancó el frame 0 del vídeo), marcas UTC y nanosegundos monotónicos.
- `ruta.gpx`: Trazado geodésico estándar con elevación y velocidad para visualizar en Google Earth, Strava o GPX Viewer.
- `session.json`: Metadatos del vehículo, adaptador, frecuencias alcanzadas por canal y estadísticas de la sesión.
- `LEEME_SINCRONIZACION.txt`: Instrucciones de importación.

### Renderizado de Telemetría en PC (Herramientas Incluidas)

En la carpeta `pc_tools/` se incluye un script listo para usar:

```bash
cd pc_tools
pip install -r requirements.txt
python render_telemetry_overlay.py --zip ruta/a/tu/sesion.zip --out video_con_telemetria.mp4
```

Este script genera el tacómetro, velocímetro y fuerzas G sincronizados cuadro a cuadro con aceleración por hardware mediante FFmpeg.

---

## 🧪 Matriz de Verificación y Estado de Pruebas

| Componente                          | Estado         | Verificación Realizada                                               |
| :---------------------------------- | :------------- | :------------------------------------------------------------------- |
| Arquitectura Room WAL + Lotes       | **Completado** | Verificado en tests unitarios (`testDebugUnitTest`).                 |
| Servicio Foreground GPS             | **Completado** | Implementado con notificación y Wakelock. Compilación limpia.        |
| Sensores Inerciales y Calibración   | **Completado** | Test unitario de proyección vectorial (`OrientationCalibratorTest`). |
| Cámara y Timestamps Monotónicos     | **Completado** | Test unitario de cálculo de offsets (`StorageAndSyncTest`).          |
| Exportación CSV / GPX / ZIP         | **Completado** | Tests de estructura y compresión (`ExportTest`, `ZipExportTest`).    |
| Protocolo ELM327 y PIDs OBD         | **Completado** | Tests con tramas reales de centralita (`Elm327ProtocolTest`).        |
| Conexión física Veepeak BLE + Coche | **Pendiente**  | Requiere prueba con el hardware físico y coche real del usuario.     |

---

## ⚖️ Licencia y Aviso de Seguridad

Esta aplicación es una herramienta de registro, aprendizaje y análisis para aficionados al automovilismo. No es un instrumento de medida homologado. Configura y activa la grabación antes de iniciar la marcha. Conduce con responsabilidad y respeta las normas de tráfico.
