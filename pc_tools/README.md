# Herramientas de PC - Telemetría Automovilística

Este directorio contiene los scripts y la documentación para procesar y renderizar los datos y vídeos grabados por la aplicación móvil en un ordenador (Windows, macOS o Linux).

---

## 1. Renderizado Automático con Python + FFmpeg (`render_telemetry_overlay.py`)

Genera un vídeo MP4 final con el salpicadero deportivo tipo MyChron (velocímetro, tacómetro de luces de cambio, G-meter de Kamm y cronómetro) superpuesto exactamente sobre el vídeo original limpio del móvil.

### Requisitos previos
1. Python 3.8 o superior.
2. FFmpeg instalado y disponible en el `PATH` del sistema (opcional pero recomendado para remuxing de audio H.264/AAC).

### Instalación de dependencias
```bash
pip install -r requirements.txt
```

### Ejecución
Directamente contra el paquete `.zip` generado por la app:
```bash
python render_telemetry_overlay.py --zip paquete_sesion.zip --out video_telemetria_final.mp4
```
O contra una carpeta descomprimida:
```bash
python render_telemetry_overlay.py --dir ruta/a/carpeta/ --out video_telemetria_final.mp4
```

---

## 2. Importación en RaceRender

Los paquetes exportados por la aplicación son compatibles con **RaceRender 3**:

1. **Añadir el vídeo**:
   * En RaceRender: Menú `File` -> `Add Video...` -> Seleccionar `video.mp4`.
2. **Añadir la telemetría**:
   * Menú `File` -> `Add Data File...` -> Seleccionar `telemetria.csv`.
3. **Sincronización instantánea (Offset 0 s)**:
   * La app calcula el tiempo exacto relativo al primer frame del vídeo en la columna `time_offset_video_sec`.
   * En la ventana de configuración de datos de RaceRender, fija:
     * **Time / Offset Column**: `time_offset_video_sec`
     * **Synchronization Offset**: `0.00` segundos.
4. **Mapeo de datos**:
   * Velocidad: Seleccionar columna `gps_speed_kmh` (Unidad: *Kilometers Per Hour*).
   * Tacómetro: Seleccionar columna `engine_rpm` (Unidad: *RPM*).
   * Acelerador: Seleccionar columna `throttle_pct` (Unidad: *Percent*).
   * G Lateral: Seleccionar columna `lateral_g` (Unidad: *G's*).
   * G Longitudinal: Seleccionar columna `longitudinal_g` (Unidad: *G's*).
   * Coordenadas GPS: `latitude` y `longitude`.

