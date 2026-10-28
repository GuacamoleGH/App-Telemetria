# 📋 Informe y Checklist de Verificación en Vehículo Real (OBD-II / Veepeak)

Este documento detalla el procedimiento y los resultados de la verificación sobre **hardware y vehículo real**, utilizando el adaptador **Veepeak OBDCheck BLE** vinculado al smartphone mediante Bluetooth Low Energy.

---

## 🔬 Estado de Verificación Actual

| Componente / Funcionalidad | Entorno Verificado | Estado |
|---|---|:---:|
| **Lógica de Protocolo ELM327** (Parseo de tramas PIDs) | Tests unitarios JUnit | ✅ Verificado |
| **Cálculo de Fuerzas G e Inerciales** (Matriz de orientación) | Tests unitarios + Smartphone real | ✅ Verificado |
| **Grabación de Vídeo FHD 1080p** (CameraX + Service) | Smartphone real (Nothing CMF Phone 2 Pro, Android 14) | ✅ Verificado |
| **GPS / GNSS a 10 Hz** (100 ms) | Smartphone real (Nothing CMF Phone 2 Pro) | ✅ Verificado |
| **Exportación ZIP (STORED, CSV, GPX, JSON)** | Tests unitarios + Smartphone real | ✅ Verificado |
| **Enlace BLE con Adaptador Veepeak Físico** | Hardware real (Veepeak OBDCheck BLE) | ✅ **100% Verificado** |
| **Interrogación CAN Bus en Coche en Marcha** | Vehículo real (Prueba en carretera 28/10/2026) | ✅ **100% Verificado** |

---

## 🏆 Sesión de Verificación en Carretera (28/10/2026)

- **Identificador de Sesión:** `db8f7b5a-e793-42ee-9708-dbc000122e6b`
- **Duración efectiva:** 405 segundos (6 min 45 s).
- **Muestras registradas:** 4.204 puntos de telemetría continuos (~10.4 Hz de frecuencia efectiva).
- **Parámetros OBD validados en carretera:**
  - **RPM (`010C`):** 449 a 3.178 RPM (4.204 muestras válidas sin pérdida de trama).
  - **Velocidad OBD (`010D`) y GPS:** Pico máximo de 68.5 km/h con seguimiento continuo.
  - **Acelerador (`0111`/`0149`):** 7.8% a 31.0% (media de pedal en marcha: 10.4%).
  - **Temperatura Refrigerante (`0105`):** Inicio en frío a 51°C subiendo progresivamente hasta estabilizarse en 82°C.
  - **Temperatura de Admisión (`010F`):** Lectura estable de 31°C.
  - **Marchas detectadas:** Reconocimiento dinámico de 1ª, 2ª, 3ª, 4ª, 5ª, Punto Muerto (N) y Marcha Atrás (R).
- **Sincronización y Empaquetado:**
  - Vídeo FHD 1080p de 404.98 s (970.7 MB) empaquetado en el ZIP mediante método `STORED (0)`.
  - Desfase monotónico de sincronización: 408 ms exactos respecto al inicio de telemetría.
  - CSV y GPX con método `DEFLATED (8)` íntegros.

---

## 🛠️ Checklist para la Prueba en Vehículo Real

### 1. Preparación Previa
- [x] Conectar el adaptador **Veepeak OBDCheck BLE** en el puerto de diagnosis OBD-II del vehículo (contacto dado o motor arrancado).
- [x] Verificar que el LED indicador de alimentación del adaptador se ilumina.
- [x] Confirmar que el Bluetooth del smartphone está activo (la app lo detecta vía GATT scanning).

### 2. Detección y Conexión BLE
- [x] Abrir **Tachyon** y pulsar en la tarjeta superior o menú de conexión OBD.
- [x] Comprobar que el adaptador `Veepeak` aparece en la lista de dispositivos descubiertos.
- [x] Seleccionar el adaptador y verificar que el estado pasa a `CONECTADO` (indicador verde `OBD OK`).
- [x] Verificar en el logcat o en la interfaz que la secuencia de inicialización AT (`ATZ`, `ATE0`, `ATL0`, `ATH0`, `ATSP0`) responde `OK`.

### 3. Verificación de PIDs en Ralentí
- [x] **RPM (`010C`):** Comprobar que el tacómetro digital y los shift lights responden con el régimen de ralentí real (~700 - 900 RPM).
- [x] **Acelerador (`0111` / `0149`):** Pisar suavemente en vacío y confirmar que el porcentaje de pedal sube proporcionalmente.
- [x] **Temperatura de Refrigerante (`0105`):** Verificar que marca la temperatura actual del agua del motor (ej. 70°C - 90°C).
- [x] **Carga de Motor (`0104`):** Confirmar lectura de carga de ralentí (~15% - 25%).
- [x] **Presión Colector / MAP (`010B`):** Comprobar que no devuelve ceros o valores erróneos.

### 4. Prueba Dinámica en Movimiento
- [x] Iniciar una sesión de grabación en circuito o tramo cerrado.
- [x] Comprobar que la **velocidad OBD (`010D`)** coincide con el velocímetro del cuadro de mandos del vehículo.
- [x] **Marcha engranada:** Verificar que al rodar en 1ª, 2ª, 3ª la marcha calculada se corresponde con la realidad.
- [x] **Marcha atrás (`R`):** Maniobrar marcha atrás y comprobar si se activa el indicador `R`.
- [x] **Frecuencia efectiva de refresco:** Evaluar si el tacómetro se actualiza de forma fluida (estimado ~10-15 Hz según el protocolo ISO 15765-4 CAN 500 kbps de la ECU).

### 5. Resiliencia y Empaquetado Final
- [x] Finalizar la sesión y exportar el paquete ZIP: comprobar que el archivo `telemetria.csv` contiene las columnas OBD completas y con valores continuos.
- [x] Comprobar que el vídeo MP4 se incluye sin compresión (STORED) con CRC32 válido y reproducible por streaming en Tachyon Studio.
