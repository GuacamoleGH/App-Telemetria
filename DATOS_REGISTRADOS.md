# 📊 Diccionario y Registro de Datos de Tachyon

Este documento detalla todos los parámetros de telemetría capturados, procesados y registrados por **Tachyon**, tanto en modo autónomo (sensores integrados del smartphone) como en modo extendido con adaptador de diagnóstico **OBD-II Bluetooth (ELM327)**.

---

## 📑 Tabla de Contenidos
1. [Arquitectura de Adquisición y Precisión](#-arquitectura-de-adquisición-y-precisión)
2. [Telemetría Estándar (Smartphone Autónomo)](#-1-telemetría-estándar-smartphone-autónomo)
   - [Posicionamiento Satelital (GPS / GNSS)](#11-posicionamiento-satelital-gps--gnss)
   - [Dinámica Inercial (IMU / Acelerómetro 3D y G-Meter 2D)](#12-dinámica-inercial-imu--acelerómetro-3d-y-g-meter-2d)
   - [Sincronización Temporal y Monotónica](#13-sincronización-temporal-y-monotónica)
   - [Métricas y Estadísticas Globales de Sesión](#14-métricas-y-estadísticas-globales-de-sesión)
3. [Telemetría de Motor y Diagnosis (Con OBD-II)](#-2-telemetría-de-motor-y-diagnosis-con-obd-ii)
   - [Parámetros Directos del Bus CAN (ECU)](#21-parámetros-directos-del-bus-can-ecu)
   - [Cálculos Sintéticos Avanzados](#22-cálculos-sintéticos-avanzados)
   - [Comportamiento según Aspiración del Motor](#23-comportamiento-según-aspiración-del-motor)
4. [Estructura del Archivo CSV de Telemetría](#-3-estructura-del-archivo-csv-de-telemetría)
5. [Estructura de Exportación GPX y JSON](#-4-estructura-de-exportación-gpx-y-json)
6. [Contenido del Paquete ZIP de Exportación](#-5-contenido-del-paquete-zip-de-exportación)

---

## ⚡ Arquitectura de Adquisición y Precisión

Tachyon implementa un sistema de registro de alta fidelidad diseñado para evitar cualquier desincronización entre vídeo y telemetría:

* **Reloj Dual Monotónico:** La cámara y los sensores se sincronizan a nivel de nanosegundos mediante `SystemClock.elapsedRealtimeNanos()` del kernel Linux/Android. Es inmune a saltos de hora UTC, cambios de huso horario o retrasos de red.
* **Escritura por Lotes (Batching):** La base de datos SQLite opera en modo **WAL (Write-Ahead Logging)**, encolando puntos en memoria y volcándolos asíncronamente en segundo plano a 10–50 Hz sin penalizar el refresco a 120 Hz de la interfaz gráfica.
* **Priorización Dinámica GATT:** Conexión Bluetooth Low Energy forzada a `CONNECTION_PRIORITY_HIGH` (intervalo de 7.5 a 11.25 ms), permitiendo hasta 15–30 consultas OBD por segundo según el vehículo.

---

## 📱 1. Telemetría Estándar (Smartphone Autónomo)

Disponible sin necesidad de ningún accesorio adicional, utilizando únicamente el hardware interno del teléfono móvil:

### 1.1 Posicionamiento Satelital (GPS / GNSS)
| Parámetro | Unidad | Frecuencia | Descripción |
|---|:---:|:---:|---|
| **Latitud** | Grados decimales (WGS84) | 1 – 10 Hz | Coordenada geográfica de latitud ($\pm0.000001^\circ$). |
| **Longitud** | Grados decimales (WGS84) | 1 – 10 Hz | Coordenada geográfica de longitud ($\pm0.000001^\circ$). |
| **Altitud** | Metros ($m$) | 1 – 10 Hz | Altitud elipsoidal sobre el nivel del mar. |
| **Velocidad GPS** | $km/h$ | 1 – 10 Hz | Velocidad geodésica real calculada por efecto Doppler del chipset GNSS. |
| **Rumbo (*Bearing*)** | Grados ($0^\circ - 359^\circ$) | 1 – 10 Hz | Dirección de desplazamiento angular respecto al norte geográfico. |
| **Precisión GPS** | Metros ($m$) | 1 – 10 Hz | Radio de incertidumbre estimado del enlace satelital con el 68% de confianza. |
| **Estado GPS Fix** | Booleano | En tiempo real | Indicador de señal satelital válida activa. |

### 1.2 Dinámica Inercial (IMU / Acelerómetro 3D y G-Meter 2D)
| Parámetro | Unidad | Frecuencia | Descripción |
|---|:---:|:---:|---|
| **Fuerza G Lateral ($G_{lat}$)** | $G$ ($1\text{ G} = 9.80665\text{ m/s}^2$) | ~50 Hz | Aceleración centrífuga en curva. Positivo = giro a la derecha, Negativo = giro a la izquierda. |
| **Fuerza G Longitudinal ($G_{long}$)** | $G$ | ~50 Hz | Aceleración en el eje de marcha del vehículo. Positivo = aceleración frontal, Negativo = frenada o marcha atrás. |
| **G Longitudinal Derivada ($dv/dt$)** | $G$ | 1 – 10 Hz | Derivada temporal de la velocidad GPS para validar y contrastar la calibración física de la IMU. |
| **Aceleración Lineal Cruda (X, Y, Z)** | $m/s^2$ | ~50 Hz | Lectura directa del sensor inercial del smartphone con gravedad sustraída. |
| **Estado de Calibración** | Estado | En tiempo real | Detección de orientación del soporte del móvil con matriz de rotación y autocalibración en reposo tras 5 s de parada. |

### 1.3 Sincronización Temporal y Monotónica
| Parámetro | Unidad | Descripción |
|---|:---:|---|
| **Timestamp UTC** | Milisegundos ($ms$) | Tiempo absoluto Unix en milisegundos desde epoch (1 de enero de 1970). |
| **Tiempo ISO 8601** | String UTC | Marca de tiempo formateada (`AAAA-MM-DDTHH:MM:SS.sssZ`). |
| **Tiempo Monotónico** | Nanosegundos ($ns$) | Contador de tiempo ininterrumpido desde el arranque del dispositivo (`SystemClock.elapsedRealtimeNanos()`). |
| **Offset Vídeo** | Segundos ($s$) | Segundo exacto transcurrido respecto al inicio de la pista de vídeo (`0.000 s`). |
| **Offset Sesión** | Segundos ($s$) | Segundo exacto transcurrido respecto al inicio de la sesión de telemetría. |

### 1.4 Métricas y Estadísticas Globales de Sesión (Historial y Resumen de Ruta)
| Métrica | Unidad | Descripción |
|---|:---:|---|
| **Distancia Total Recorrida** | Kilómetros ($km$) | Sumatorio acumulativo de distancia geodésica mediante coordenadas GPS de alta frecuencia. |
| **Tiempo de Sesión** | Formato `MM:SS` o `HH:MM:SS` | Duración total cronometrada del trayecto. |
| **Tiempo en Movimiento** | Formato `MM:SS` (%) | Tiempo efectivo rodando a velocidad $\ge 3\text{ km/h}$ y porcentaje del tiempo total. |
| **Tiempo Detenido** | Formato `MM:SS` (%) | Tiempo estacionado o detenido en semáforos/tráfico ($v < 3\text{ km/h}$). |
| **Horario de Trayecto** | `HH:MM ➔ HH:MM` | Marca horaria de inicio y finalización del viaje. |
| **Velocidad Máxima** | $km/h$ | Pico más alto de velocidad registrado durante la sesión. |
| **Velocidad Media Global** | $km/h$ | Media aritmética ponderada de velocidad sobre el tiempo total. |
| **Velocidad Media en Marcha** | $km/h$ | Velocidad media real en movimiento (excluyendo semáforos y paradas). |
| **Ritmo de Marcha** | $min/km$ | Tiempo medio necesario para recorrer un kilómetro. |
| **Paradas en Ruta** | Número de detenciones | Conteo inteligente de detenciones en semáforos o retenciones (transiciones $v \ge 10\text{ km/h} \to < 2.5\text{ km/h}$). |
| **Consumo Medio** | $L/100\text{ km}$ | Media de consumo de combustible registrada por OBD-II o integrada en el recorrido. |
| **Combustible Total Consumido** | Litros ($L$) | Integración numérica precisa del combustible quemado durante todo el trayecto (incluyendo ralentí). |
| **Coste Estimado del Viaje** | Euros (€) | Estimación económica del gasto en combustible (@ 1.55 €/L). |
| **Consumo Eco Crucero** | $L/100\text{ km}$ | Mínimo consumo sostenido a velocidad de crucero ($\ge 50\text{ km/h}$). |
| **Pedal Acelerador Máximo** | Porcentaje ($0 - 100\%$) | Porcentaje máximo de pedal pisado alcanzado en la sesión. |
| **Pedal Acelerador Medio** | Porcentaje ($0 - 100\%$) | Media de recorrido de acelerador durante la marcha activa. |
| **Evolución Térmica del Motor** | Grados Celsius ($^\circ C$) | Temperatura del refrigerante al iniciar el viaje frente a la temperatura final alcanzada (`Ini ➔ Fin`) y pico máximo. |
| **Pico G Lateral Máximo** | $G$ | Máxima fuerza centrífuga alcanzada en curva (desglosada en curva izquierda y derecha). |
| **Pico G Longitudinal Máximo** | $G$ | Máxima deceleración en frenada ($G < 0$) o empuje en aceleración ($G > 0$). |
| **Fuerza G Combinada Máxima** | $G$ | Vector resultante total $\sqrt{G_{lat}^2 + G_{long}^2}$. |
| **Desnivel Acumulado (+ / -)** | Metros ($m$) | Ganancia y pérdida de altitud vertical acumuladas durante la ruta. |
| **Altitud Máxima y Mínima** | Metros ($m$) | Cota máxima y mínima sobre el nivel del mar registradas por el barómetro/GNSS. |

---

## 🔌 2. Telemetría de Motor y Diagnosis (Con OBD-II)

Cuando se vincula un escáner **OBD-II Bluetooth Low Energy (ELM327)**, Tachyon activa la lectura del tren motriz del vehículo:

### 2.1 Parámetros Directos del Bus CAN (ECU)
| Parámetro | PID Modo 01 | Unidad | Prioridad de Muestreo | Descripción |
|---|:---:|:---:|:---:|---|
| **Régimen de Giro (RPM)** | `010C` | $RPM$ ($rev/min$) | **Máxima (cada ciclo)** | Revoluciones por minuto del cigüeñal del motor (resolución de 0.25 RPM). |
| **Velocidad de Rueda OBD** | `010D` | $km/h$ | **Máxima (cada ciclo)** | Velocidad medida por los sensores ABS/caja de cambios del coche (sin margen de error por satélites). |
| **Posición del Pedal / Mariposa** | `0111` o `0149` | Porcentaje ($0 - 100\%$) | **Alta (cada ciclo)** | Posición relativa de la mariposa de admisión o del pedal acelerador. |
| **Presión Colector de Admisión (MAP)** | `010B` | $kPa$ ($0 - 255\text{ kPa}$) | **Media (cada 2 ciclos)** | Presión absoluta en el colector de admisión tras la mariposa. |
| **Caudal de Combustible Directo** | `015E` | $L/h$ | **Regular (cada 4 ciclos)** | Caudal volumétrico de combustible consumido por el motor (*Engine Fuel Rate*). |
| **Caudalímetro de Aire (MAF)** | `0110` | Gramos/segundo ($g/s$) | **Regular (cada 4 ciclos)** | Flujo másico de aire aspirado por los cilindros. |
| **Marcha de Transmisión Real** | `01A4` | Marcha (`1-8, N, R`) | **Regular (cada 4 ciclos)** | Marcha física informada directamente por la ECU de la transmisión. |
| **Temperatura del Refrigerante** | `0105` | Grados Celsius ($^\circ C$) | **Baja (cada 15 ciclos)** | Temperatura del líquido anticongelante/agua del bloque motor. |
| **Carga de Motor Calculada** | `0104` | Porcentaje ($0 - 100\%$) | **Baja (cada 15 ciclos)** | Relación entre el par actual y el par máximo disponible a esas RPM. |
| **Temperatura Aire Admisión (IAT)** | `010F` | Grados Celsius ($^\circ C$) | **Baja (cada 15 ciclos)** | Temperatura del aire que ingresa al colector tras el intercooler/filtro. |
| **Presión Barométrica Ambiental** | `0133` | $kPa$ | **Baja (cada 15 ciclos)** | Presión atmosférica local (referencia para cálculo de sobrealimentación). |

### 2.2 Cálculos Sintéticos Avanzados

Para no sobrecargar el bus CAN del coche, Tachyon realiza cálculos derivados en tiempo real en la CPU del smartphone con coste 0 en el bus:

1. **Presión de Sobrealimentación (Turbo / Boost):**
   $$P_{\text{boost}} (\text{bar}) = \frac{P_{\text{MAP}} (\text{kPa}) - P_{\text{baro}} (\text{kPa})}{100}$$
   Calcula la presión manométrica positiva real generada por el turbo o compresor volumétrico respecto a la atmósfera ambiental.
2. **Consumo Instantáneo ($L/h$ y $L/100\text{ km}$):**
   - Vía PID `015E` si está disponible.
   - O vía fórmula estequiométrica sobre el caudalímetro MAF:
     $$\text{Consumo } (L/h) = \frac{\text{MAF } (g/s) \times 3600}{14.7 \times 740}$$
     $$\text{Consumo } (L/100\text{ km}) = \frac{\text{Consumo } (L/h)}{v_{\text{coche}} (km/h)} \times 100$$
3. **Cálculo Físico de Marcha Engranada (`1ª` a `7ª`, `N`, `R`):**
   - **En movimiento frontal:** Correlación continua entre el régimen de giro ($RPM$) y la velocidad ($km/h$):
     $$\text{Ratio} = \frac{\text{RPM}}{v_{\text{km/h}}}$$
   - **En marcha atrás (`R`):** Detección combinada inercial-dinámica:
     $$v > 1.5\text{ km/h} \quad\land\quad \text{Pedal Acelerador} > 5\% \quad\land\quad G_{\text{long}} < -0.10\text{ G}$$
     Al pisar el acelerador con el coche desplazándose hacia atrás, la fuerza inercial empuja hacia la trasera del vehículo ($G < 0$), diferenciando la marcha atrás de una frenada (donde el acelerador está al $0\%$).

### 2.3 Comportamiento según Aspiración del Motor

En **Ajustes de Vehículo** es posible seleccionar la configuración del motor:
* **🍃 Atmosférico (NA):** No muestra valores de turbo negativos derivados del vacío en admisión. En su lugar, el cuadro de mandos destaca en tiempo real la **Carga de Motor (`%`)** o el **Consumo (`L/100 km`)**.
* **⚡ Turbo / Sobrealimentado:** Muestra la presión positiva del turbo en bares (`+0.45 bar`) tanto en la tarjeta de métricas como en los datos exportados.

---

## 📑 3. Estructura del Archivo CSV de Telemetría

El archivo `telemetria.csv` exportado es compatible de forma nativa con **RaceRender**, **DashWare**, **MoTeC i2 Pro**, **Excel** y scripts de **Python (Pandas)**:

```csv
time_offset_video_sec,time_offset_session_sec,timestamp_utc_ms,iso_time,monotonic_nanos,latitude,longitude,altitude_m,gps_speed_kmh,bearing_deg,accuracy_m,lateral_g,longitudinal_g,engine_rpm,obd_speed_kmh,throttle_pct,coolant_temp_c,gear,boost_bar,fuel_l100km,intake_temp_c,accel_x,accel_y,accel_z
```

### Descripción de Columnas

| # | Columna | Tipo | Unidad | Ejemplo | Origen |
|:---:|---|:---:|:---:|:---:|---|
| **1** | `time_offset_video_sec` | Decimal | Segundos ($s$) | `12.450` | Offset relativo a la pista de vídeo FHD sincronizada. |
| **2** | `time_offset_session_sec` | Decimal | Segundos ($s$) | `12.450` | Offset relativo al primer punto de la sesión. |
| **3** | `timestamp_utc_ms` | Entero | Milisegundos ($ms$) | `1729864200123` | Timestamp Unix UTC absoluto. |
| **4** | `iso_time` | Texto | ISO 8601 | `2026-10-27T10:30:00.123Z` | Fecha y hora UTC legible. |
| **5** | `monotonic_nanos` | Entero | Nanosegundos ($ns$) | `154823948572000` | Reloj monotónico de hardware sin deriva. |
| **6** | `latitude` | Decimal | Grados ($^\circ$) | `40.416775` | Latitud WGS84 del GPS. |
| **7** | `longitude` | Decimal | Grados ($^\circ$) | `-3.703790` | Longitud WGS84 del GPS. |
| **8** | `altitude_m` | Decimal | Metros ($m$) | `655.4` | Altitud sobre el nivel del mar. |
| **9** | `gps_speed_kmh` | Decimal | $km/h$ | `124.6` | Velocidad por satélite Doppler. |
| **10** | `bearing_deg` | Decimal | Grados ($0-359^\circ$) | `184.2` | Rumbo de desplazamiento. |
| **11** | `accuracy_m` | Decimal | Metros ($m$) | `1.8` | Margen de error horizontal GPS. |
| **12** | `lateral_g` | Decimal | $G$ | `0.85` | Aceleración centrífuga en curva. |
| **13** | `longitudinal_g` | Decimal | $G$ | `-0.92` | Aceleración/Frenada en línea recta. |
| **14** | `engine_rpm` | Entero | $RPM$ | `5850` | Revoluciones por minuto del motor (OBD). |
| **15** | `obd_speed_kmh` | Decimal | $km/h$ | `123.0` | Velocidad de giro de ruedas (OBD). |
| **16** | `throttle_pct` | Decimal | $\%$ ($0-100$) | `100.0` | Porcentaje de pedal de acelerador pisado. |
| **17** | `coolant_temp_c` | Entero | $^\circ C$ | `92` | Temperatura del líquido refrigerante. |
| **18** | `gear` | Texto | Carácter | `3` | Marcha engranada (`1` a `7`, `N`, `R`). |
| **19** | `boost_bar` | Decimal | $bar$ | `0.45` | Presión positiva del turbo (relativa). |
| **20** | `fuel_l100km` | Decimal | $L/100\text{ km}$ | `14.2` | Consumo de combustible instantáneo. |
| **21** | `intake_temp_c` | Entero | $^\circ C$ | `34` | Temperatura del aire en admisión (IAT). |
| **22** | `accel_x` | Decimal | $m/s^2$ | `0.12` | Vector inercial X crudo del smartphone. |
| **23** | `accel_y` | Decimal | $m/s^2$ | `8.25` | Vector inercial Y crudo del smartphone. |
| **24** | `accel_z` | Decimal | $m/s^2$ | `-1.14` | Vector inercial Z crudo del smartphone. |

---

## 🗺️ 4. Estructura de Exportación GPX y JSON

### 4.1 Archivo GPX (`sesion.gpx`)
* Cumple el estándar **GPX 1.1** con extensiones de trackpoint (`<trkpt>`).
* Compatible con **Strava**, **Garmin Connect**, **Google Earth** y visores GIS.
* Incluye etiquetas de velocidad nativas `<speed>` y extensiones de cadencia/revoluciones (`<gpxtpx:TrackPointExtension>` con `<gpxtpx:hr>` o extensiones de telemetría).

### 4.2 Archivo de Metadatos JSON (`session.json`)
Contiene el esquema técnico completo de la sesión (`schema_version`: `"1.1.0"`):
* `schema_version`: Versión del esquema (`1.1.0`).
* `session`: Identificador UUID único, fechas y horas (ISO 8601 y Unix epoch), distancia total ($m$), velocidades ($km/h$) y conteo de muestras.
* `vehicle_config`: Configuración de motor (régimen de corte, redline y shift light).
* `video_synchronization`:
  * `has_video`: Booleano indicando si existe vídeo grabado.
  * `video_filename`: Nombre estándar del vídeo (`video.mp4`).
  * `video_storage_mode`: `"embedded"` (guardado dentro del ZIP en modo `STORED`) o `"external"` (guardado como archivo suelto junto al ZIP si supera el umbral de 2 GB).
  * `external_video_filename`: Nombre del archivo de vídeo externo (ej. `sesion_xxx_video.mp4`) cuando `video_storage_mode` es `"external"`.
  * `video_start_monotonic_nanos`: Timestamp de reloj monotónico exacto del primer fotograma.
  * `telemetry_to_video_offset_ms`: Diferencial de sincronización inicial en milisegundos.
  * `sync_formula`: Ecuación matemática de alineación fotograma a muestra.

---

## 📦 5. Contenido del Paquete ZIP de Exportación

Al pulsar el botón **Exportar Paquete ZIP** desde el detalle de la sesión en el historial, la aplicación genera un archivo comprimido autónomo en `Download/Telemetria/sesion_[id]_bundle.zip` con la siguiente estructura y especificaciones:

```
📁 sesion_fafeedb7_bundle.zip
│
├── 🎥 video.mp4                 <- Guardado con método STORED (método 0, sin recompresión)
├── 📊 telemetria.csv            <- Telemetría completa muestreada (método DEFLATED)
├── 🗺️ ruta.gpx                  <- Trazado satelital GPX 1.1 (método DEFLATED)
├── ⚙️ session.json              <- Metadatos de sincronización y configuración v1.1.0 (método DEFLATED)
└── 📄 LEEME_SINCRONIZACION.txt  <- Guía de sincronización para PC (método DEFLATED)
```

### Especificaciones técnicas del almacenamiento de vídeo:
1. **Método STORED (Sin compresión):** El archivo `video.mp4` se empaqueta con método `STORED` calculando previamente su CRC-32 mediante streaming por bloques de 64 KB (sin consumo de memoria RAM). Esto permite a reproductores y a **Tachyon Studio (PC)** leer o reproducir el vídeo directamente desde el contenedor ZIP mediante `data_offset` sin necesidad de extraerlo previamente al disco temporal.
2. **Umbral de seguridad (ZIP64 / 2 GB):** `ZipOutputStream` en ciertas versiones de Android presenta limitaciones e inconsistencias con archivos superiores a 2 o 4 GB en sistemas de 32 bits. Si el archivo de vídeo supera los **2 GB** (`2.147.483.648 bytes`), la aplicación lo exporta automáticamente como archivo suelto junto al ZIP (`sesion_[id]_bundle_video.mp4`) y lo referencia en `session.json` (`"video_storage_mode": "external"`), evitando cualquier corrupción del archivo ZIP.

---

> [!TIP]
> Si no dispones de lector OBD-II conectado, todos los campos dependientes del motor permanecerán vacíos o nulos en el CSV, pero **la totalidad de las métricas de vídeo, GPS, dinámicas inerciales (G-Force) y sincronización temporal funcionarán exactamente con la misma precisión**.
