# 📋 Checklist de Pruebas Pendientes en Vehículo Real (OBD-II / Veepeak)

Este documento detalla el procedimiento de verificación pendiente sobre **hardware y vehículo real**, utilizando el adaptador **Veepeak OBDCheck BLE** vinculado al smartphone mediante Bluetooth Low Energy.

---

## 🔬 Estado de Verificación Actual

| Componente / Funcionalidad | Entorno Verificado | Estado |
|---|---|:---:|
| **Lógica de Protocolo ELM327** (Parseo de tramas PIDs) | Tests unitarios JUnit | ✅ Verificado |
| **Cálculo de Fuerzas G e Inerciales** (Matriz de orientación) | Tests unitarios + Smartphone real | ✅ Verificado |
| **Grabación de Vídeo FHD 1080p** (CameraX + Service) | Smartphone real (Nothing CMF Phone 2 Pro, Android 14) | ✅ Verificado |
| **GPS / GNSS a 10 Hz** (100 ms) | Smartphone real (Nothing CMF Phone 2 Pro) | ✅ Verificado |
| **Exportación ZIP (STORED, CSV, GPX, JSON)** | Tests unitarios + Smartphone real | ✅ Verificado |
| **Enlace BLE con Adaptador Veepeak Físico** | Hardware real | ⏳ **Pendiente** |
| **Interrogación CAN Bus en Coche en Marcha** | Vehículo real | ⏳ **Pendiente** |

---

## 🛠️ Checklist para la Prueba en Vehículo Real

### 1. Preparación Previa
- [ ] Conectar el adaptador **Veepeak OBDCheck BLE** en el puerto de diagnosis OBD-II del vehículo (contacto dado o motor arrancado).
- [ ] Verificar que el LED indicador de alimentación del adaptador se ilumina.
- [ ] Confirmar que el Bluetooth del smartphone está activo (no es necesario emparejarlo manualmente desde los ajustes del sistema; la app lo detecta vía GATT scanning).

### 2. Detección y Conexión BLE
- [ ] Abrir **Tachyon** y pulsar en la tarjeta superior o menú de conexión OBD.
- [ ] Comprobar que el adaptador `Veepeak` aparece en la lista de dispositivos descubiertos.
- [ ] Seleccionar el adaptador y verificar que el estado pasa a `CONECTADO` (indicador verde `OBD OK`).
- [ ] Verificar en el logcat o en la interfaz que la secuencia de inicialización AT (`ATZ`, `ATE0`, `ATL0`, `ATH0`, `ATSP0`) responde `OK`.

### 3. Verificación de PIDs en Ralentí
- [ ] **RPM (`010C`):** Comprobar que el tacómetro digital y los shift lights responden con el régimen de ralentí real (~700 - 900 RPM).
- [ ] **Acelerador (`0111` / `0149`):** Pisar suavemente en vacío y confirmar que el porcentaje de pedal sube proporcionalmente.
- [ ] **Temperatura de Refrigerante (`0105`):** Verificar que marca la temperatura actual del agua del motor (ej. 70°C - 90°C).
- [ ] **Carga de Motor (`0104`):** Confirmar lectura de carga de ralentí (~15% - 25%).
- [ ] **Presión Colector / MAP (`010B`):** Comprobar que no devuelve ceros o valores erróneos.

### 4. Prueba Dinámica en Movimiento
- [ ] Iniciar una sesión de grabación en circuito o tramo cerrado.
- [ ] Comprobar que la **velocidad OBD (`010D`)** coincide con el velocímetro del cuadro de mandos del vehículo.
- [ ] **Marcha engranada:** Verificar que al rodar en 1ª, 2ª, 3ª la marcha calculada se corresponde con la realidad.
- [ ] **Marcha atrás (`R`):** Maniobrar marcha atrás y comprobar si se activa el indicador `R`.
- [ ] **Frecuencia efectiva de refresco:** Evaluar si el tacómetro se actualiza de forma fluida (estimado ~15-20 Hz según el protocolo ISO 15765-4 CAN 500 kbps de la ECU).

### 5. Resiliencia ante Desconexión
- [ ] Apagar el contacto del coche con la grabación activa: comprobar que la app maneja la pérdida de señal sin congelarse ni cerrarse inesperadamente.
- [ ] Volver a dar contacto: verificar que el servicio reconecta automáticamente y reanuda el polling de datos.
- [ ] Finalizar la sesión y exportar el paquete ZIP: comprobar que el archivo `telemetria.csv` contiene las columnas OBD completas y con valores continuos.
