#!/usr/bin/env python3
"""
Renderizador de Overlay de Telemetría para PC (Python + OpenCV / FFmpeg).
Superpone los relojes e instrumentación estilo MyChron 5 sobre el vídeo grabado
por la app con sincronización precisa mediante las marcas de tiempo monotónicas.

Uso:
    python render_telemetry_overlay.py --zip paquete_sesion.zip --out video_con_telemetria.mp4
    python render_telemetry_overlay.py --dir ruta/a/carpeta_descomprimida/ --out video_final.mp4
"""

import argparse
import csv
import json
import math
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

try:
    import cv2
    import numpy as np
except ImportError:
    print("[ERROR] Se requiere 'opencv-python' y 'numpy'. Instálalos con:")
    print("    pip install -r requirements.txt")
    sys.exit(1)


def parse_telemetry_csv(csv_path):
    """Lee el CSV y devuelve una lista de diccionarios ordenados por time_offset_video_sec."""
    data = []
    with open(csv_path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            try:
                item = {
                    "t_video": float(row.get("time_offset_video_sec") or 0.0),
                    "speed_kmh": float(row.get("gps_speed_kmh") or 0.0) if row.get("gps_speed_kmh") else None,
                    "lat_g": float(row.get("lateral_g") or 0.0) if row.get("lateral_g") else 0.0,
                    "long_g": float(row.get("longitudinal_g") or 0.0) if row.get("longitudinal_g") else 0.0,
                    "rpm": int(float(row.get("engine_rpm"))) if row.get("engine_rpm") else None,
                    "throttle": float(row.get("throttle_pct")) if row.get("throttle_pct") else None,
                }
                data.append(item)
            except Exception:
                continue
    data.sort(key=lambda x: x["t_video"])
    return data


def interpolate_telemetry(data, current_time):
    """Encuentra o interpola linealmente los valores de telemetría para el instante de tiempo t del vídeo."""
    if not data:
        return {"speed_kmh": 0.0, "lat_g": 0.0, "long_g": 0.0, "rpm": None, "throttle": None}

    if current_time <= data[0]["t_video"]:
        return data[0]
    if current_time >= data[-1]["t_video"]:
        return data[-1]

    # Búsqueda binaria simple
    low = 0
    high = len(data) - 1
    while low <= high:
        mid = (low + high) // 2
        if data[mid]["t_video"] < current_time:
            low = mid + 1
        else:
            high = mid - 1

    p1 = data[max(0, low - 1)]
    p2 = data[min(len(data) - 1, low)]

    dt = p2["t_video"] - p1["t_video"]
    if dt <= 0.0001:
        return p1

    factor = (current_time - p1["t_video"]) / dt

    speed = None
    if p1["speed_kmh"] is not None and p2["speed_kmh"] is not None:
        speed = p1["speed_kmh"] + factor * (p2["speed_kmh"] - p1["speed_kmh"])

    lat_g = p1["lat_g"] + factor * (p2["lat_g"] - p1["lat_g"])
    long_g = p1["long_g"] + factor * (p2["long_g"] - p1["long_g"])

    rpm = None
    if p1["rpm"] is not None and p2["rpm"] is not None:
        rpm = int(p1["rpm"] + factor * (p2["rpm"] - p1["rpm"]))

    throttle = None
    if p1["throttle"] is not None and p2["throttle"] is not None:
        throttle = p1["throttle"] + factor * (p2["throttle"] - p1["throttle"])

    return {
        "speed_kmh": speed or 0.0,
        "lat_g": lat_g,
        "long_g": long_g,
        "rpm": rpm,
        "throttle": throttle
    }


def draw_overlay(frame, tel_data, current_time_sec, vehicle_config=None):
    """Dibuja el velocímetro, tacómetro y G-meter estilo deportivo sobre el frame de vídeo."""
    h, w, _ = frame.shape
    config = vehicle_config or {}
    max_rpm = config.get("max_rpm", 7500)
    redline_rpm = config.get("redline_rpm", 6500)
    shift_light_rpm = config.get("shift_light_rpm", max(1000, redline_rpm - 500))

    # 1. Tacómetro superior (luces de cambio y tacómetro estilo competición / MyChron 5)
    rpm = tel_data.get("rpm")
    if rpm is not None:
        active_ratio = min(1.0, max(0.0, rpm / max_rpm))
        bar_w = int(w * 0.5)
        bar_h = 18
        bar_x = (w - bar_w) // 2
        bar_y = 35

        is_shift = rpm >= shift_light_rpm
        # Parpadeo rápido a 8 Hz cuando se alcanza el régimen de cambio (Shift Light Strobe)
        flash_state = (int(current_time_sec * 8) % 2 == 0) if is_shift else False

        # Fondo del tacómetro
        bg_border_color = (0, 0, 255) if (is_shift and flash_state) else (50, 60, 75)
        cv2.rectangle(frame, (bar_x - 6, bar_y - 6), (bar_x + bar_w + 6, bar_y + bar_h + 6), (16, 20, 26), -1)
        cv2.rectangle(frame, (bar_x - 6, bar_y - 6), (bar_x + bar_w + 6, bar_y + bar_h + 6), bg_border_color, 1)

        segments = 24
        active_segs = int(active_ratio * segments)
        seg_w = bar_w // segments

        for i in range(segments):
            sx = bar_x + i * seg_w
            seg_rpm = ((i + 1) / segments) * max_rpm

            if i < active_segs:
                if is_shift and flash_state:
                    # En régimen de corte/cambio, luces parpadean en blanco/magenta de competición
                    color = (255, 100, 255) if i >= int(segments * 0.75) else (255, 255, 255)
                elif seg_rpm < (redline_rpm * 0.75):
                    color = (100, 240, 0) # Verde BGR
                elif seg_rpm < redline_rpm:
                    color = (0, 210, 255) # Ámbar BGR
                else:
                    color = (40, 40, 255) # Rojo Redline BGR
            else:
                color = (32, 40, 48)
            cv2.rectangle(frame, (sx + 2, bar_y), (sx + seg_w - 2, bar_y + bar_h), color, -1)

        # Texto de RPM y aviso SHIFT
        rpm_str = f"{rpm} RPM"
        cv2.putText(frame, rpm_str, (bar_x + bar_w + 14, bar_y + 15), cv2.FONT_HERSHEY_DUPLEX, 0.6, (255, 255, 255), 1, cv2.LINE_AA)

        if is_shift and flash_state:
            # Cartel "⚡ SHIFT" parpadeante
            shift_text = "SHIFT!"
            cv2.rectangle(frame, (bar_x - 70, bar_y - 3), (bar_x - 12, bar_y + bar_h + 3), (40, 40, 255), -1)
            cv2.putText(frame, shift_text, (bar_x - 66, bar_y + 14), cv2.FONT_HERSHEY_DUPLEX, 0.5, (255, 255, 255), 1, cv2.LINE_AA)

    # 2. Panel inferior izquierdo: Velocímetro digital grande
    panel_w = 260
    panel_h = 120
    px = 40
    py = h - panel_h - 40

    overlay_box = frame[py:py+panel_h, px:px+panel_w].copy()
    cv2.rectangle(overlay_box, (0, 0), (panel_w, panel_h), (10, 14, 18), -1)
    cv2.addWeighted(overlay_box, 0.75, frame[py:py+panel_h, px:px+panel_w], 0.25, 0, frame[py:py+panel_h, px:px+panel_w])
    cv2.rectangle(frame, (px, py), (px + panel_w, py + panel_h), (50, 60, 75), 1)

    speed_str = f"{tel_data['speed_kmh']:.1f}"
    cv2.putText(frame, speed_str, (px + 20, py + 75), cv2.FONT_HERSHEY_DUPLEX, 2.2, (100, 255, 120), 3, cv2.LINE_AA)
    cv2.putText(frame, "KM/H", (px + 175, py + 72), cv2.FONT_HERSHEY_DUPLEX, 0.7, (180, 190, 200), 1, cv2.LINE_AA)
    cv2.putText(frame, "GPS SPEED", (px + 22, py + 105), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (100, 255, 120), 1, cv2.LINE_AA)

    # 3. Panel inferior derecho: G-Meter circular
    gm_size = 140
    gmx = w - gm_size - 40
    gmy = h - gm_size - 40

    gm_box = frame[gmy:gmy+gm_size, gmx:gmx+gm_size].copy()
    cv2.rectangle(gm_box, (0, 0), (gm_size, gm_size), (10, 14, 18), -1)
    cv2.addWeighted(gm_box, 0.75, frame[gmy:gmy+gm_size, gmx:gmx+gm_size], 0.25, 0, frame[gmy:gmy+gm_size, gmx:gmx+gm_size])
    cv2.rectangle(frame, (gmx, gmy), (gmx + gm_size, gmy + gm_size), (50, 60, 75), 1)

    center = (gmx + gm_size // 2, gmy + gm_size // 2)
    max_radius = gm_size // 2 - 14

    # Círculos de escala 0.5G y 1.0G
    cv2.circle(frame, center, int(max_radius * 0.5), (45, 55, 65), 1)
    cv2.circle(frame, center, max_radius, (60, 75, 90), 1)
    cv2.line(frame, (center[0] - max_radius, center[1]), (center[0] + max_radius, center[1]), (45, 55, 65), 1)
    cv2.line(frame, (center[0], center[1] - max_radius), (center[0], center[1] + max_radius), (45, 55, 65), 1)

    # Punto de Fuerza G
    max_scale_g = 1.5
    lat_norm = max(-1.0, min(1.0, tel_data["lat_g"] / max_scale_g))
    long_norm = max(-1.0, min(1.0, tel_data["long_g"] / max_scale_g))

    dot_x = int(center[0] + lat_norm * max_radius)
    dot_y = int(center[1] - long_norm * max_radius)

    cv2.circle(frame, (dot_x, dot_y), 8, (255, 230, 0), -1) # Cyan BGR
    cv2.circle(frame, (dot_x, dot_y), 4, (255, 255, 255), -1)

    cv2.putText(frame, f"Lat: {tel_data['lat_g']:+.2f}G", (gmx + 10, gmy + 16), cv2.FONT_HERSHEY_SIMPLEX, 0.38, (255, 230, 0), 1, cv2.LINE_AA)
    cv2.putText(frame, f"Long: {tel_data['long_g']:+.2f}G", (gmx + 10, gmy + 30), cv2.FONT_HERSHEY_SIMPLEX, 0.38, (100, 255, 120), 1, cv2.LINE_AA)

    # 4. Cronómetro en cabecera
    minutes = int(current_time_sec // 60)
    seconds = int(current_time_sec % 60)
    millis = int((current_time_sec - int(current_time_sec)) * 10)
    time_str = f"{minutes:02d}:{seconds:02d}.{millis}"
    cv2.putText(frame, time_str, (40, 50), cv2.FONT_HERSHEY_DUPLEX, 0.9, (240, 245, 250), 1, cv2.LINE_AA)


def process_video(video_path, csv_path, output_path, vehicle_config=None, rotate=0):
    print(f"[INFO] Abriendo vídeo original: {video_path}")
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        print(f"[ERROR] No se pudo abrir el vídeo: {video_path}")
        return False

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    raw_width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    raw_height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

    # Si se rota 90 o 270 grados, las dimensiones se invierten
    if rotate in (90, 270):
        width, height = raw_height, raw_width
    else:
        width, height = raw_width, raw_height

    print(f"[INFO] Resolución: {width}x{height} | FPS: {fps:.2f} | Frames totales: {total_frames} | Rotación: {rotate}°")
    if vehicle_config:
        print(f"[INFO] Configuración de vehículo: Max RPM={vehicle_config.get('max_rpm')}, Redline={vehicle_config.get('redline_rpm')}, Shift={vehicle_config.get('shift_light_rpm')}")

    tel_data = parse_telemetry_csv(csv_path)
    print(f"[INFO] Muestras de telemetría cargadas: {len(tel_data)}")

    # Crear vídeo temporal sin audio para el render
    temp_dir = tempfile.mkdtemp()
    raw_rendered_video = os.path.join(temp_dir, "rendered_raw.mp4")

    fourcc = cv2.VideoWriter_fourcc(*"mp4v")
    writer = cv2.VideoWriter(raw_rendered_video, fourcc, fps, (width, height))

    frame_idx = 0
    print("[INFO] Renderizando overlay cuadro a cuadro...")

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        # Corregir orientación del frame de cámara si está invertido
        if rotate == 180:
            frame = cv2.rotate(frame, cv2.ROTATE_180)
        elif rotate == 90:
            frame = cv2.rotate(frame, cv2.ROTATE_90_CLOCKWISE)
        elif rotate == 270:
            frame = cv2.rotate(frame, cv2.ROTATE_90_COUNTERCLOCKWISE)

        current_time_sec = frame_idx / fps
        interpolated = interpolate_telemetry(tel_data, current_time_sec)
        draw_overlay(frame, interpolated, current_time_sec, vehicle_config)
        writer.write(frame)

        frame_idx += 1
        if frame_idx % 150 == 0 or frame_idx == total_frames:
            progress = (frame_idx / total_frames) * 100.0 if total_frames > 0 else 0
            print(f"       Progreso: {frame_idx}/{total_frames} ({progress:.1f}%)")

    cap.release()
    writer.release()
    print("[INFO] Render de vídeo completado.")

    # Muxing de audio original usando FFmpeg si está disponible
    ffmpeg_bin = shutil.which("ffmpeg")
    if ffmpeg_bin:
        print("[INFO] FFmpeg detectado. Remuxing de audio original y codificación H.264...")
        cmd = [
            ffmpeg_bin, "-y",
            "-i", raw_rendered_video,
            "-i", video_path,
            "-map", "0:v:0",
            "-map", "1:a:0?",
            "-c:v", "libx264",
            "-preset", "fast",
            "-crf", "18",
            "-c:a", "aac",
            output_path
        ]
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if res.returncode == 0:
            print(f"[SUCCESS] Vídeo final exportado con éxito en: {output_path}")
        else:
            print("[WARN] Error en FFmpeg remuxing, usando archivo directo.")
            shutil.copyfile(raw_rendered_video, output_path)
    else:
        print("[WARN] FFmpeg no encontrado en PATH. El vídeo se guardará sin la pista de audio original.")
        shutil.copyfile(raw_rendered_video, output_path)

    shutil.rmtree(temp_dir, ignore_errors=True)
    return True


def main():
    parser = argparse.ArgumentParser(description="Renderiza telemetría sincronizada sobre vídeo de la app.")
    parser.add_argument("--zip", help="Ruta al archivo .zip exportado por la app.")
    parser.add_argument("--dir", help="Ruta a la carpeta con los archivos de sesión extraídos.")
    parser.add_argument("--out", default="telemetria_overlay.mp4", help="Nombre del archivo MP4 de salida.")
    parser.add_argument("--max-rpm", type=int, help="Override de RPM máximas para el tacómetro.")
    parser.add_argument("--redline-rpm", type=int, help="Override de RPM donde empieza la línea roja.")
    parser.add_argument("--shift-rpm", type=int, help="Override de RPM para las luces de cambio (shift light).")
    parser.add_argument("--rotate", type=int, choices=[0, 90, 180, 270], default=0, help="Rotar el vídeo base en grados (útil si el móvil grabó invertido 180°).")
    args = parser.parse_args()

    if not args.zip and not args.dir:
        print("[ERROR] Debes especificar --zip <archivo.zip> o --dir <carpeta_sesion>")
        sys.exit(1)

    work_dir = ""
    clean_temp = False

    if args.zip:
        if not os.path.exists(args.zip):
            print(f"[ERROR] No existe el archivo: {args.zip}")
            sys.exit(1)
        work_dir = tempfile.mkdtemp()
        clean_temp = True
        print(f"[INFO] Descomprimiendo paquete ZIP en {work_dir}...")
        with zipfile.ZipFile(args.zip, "r") as z:
            z.extractall(work_dir)
    else:
        work_dir = args.dir

    video_file = os.path.join(work_dir, "video.mp4")
    csv_file = os.path.join(work_dir, "telemetria.csv")
    json_file = os.path.join(work_dir, "session.json")

    if not os.path.exists(video_file):
        print(f"[ERROR] No se encontró 'video.mp4' en {work_dir}")
        sys.exit(1)
    if not os.path.exists(csv_file):
        print(f"[ERROR] No se encontró 'telemetria.csv' en {work_dir}")
        sys.exit(1)

    # Cargar configuración del vehículo desde session.json si está disponible
    vehicle_config = {
        "max_rpm": 7500,
        "redline_rpm": 6500,
        "shift_light_rpm": 6000
    }
    if os.path.exists(json_file):
        try:
            with open(json_file, "r", encoding="utf-8") as jf:
                session_meta = json.load(jf)
                if "vehicle_config" in session_meta:
                    vc = session_meta["vehicle_config"]
                    vehicle_config["max_rpm"] = vc.get("max_rpm", vehicle_config["max_rpm"])
                    vehicle_config["redline_rpm"] = vc.get("redline_rpm", vehicle_config["redline_rpm"])
                    vehicle_config["shift_light_rpm"] = vc.get("shift_light_rpm", vehicle_config["shift_light_rpm"])
        except Exception as e:
            print(f"[WARN] Error leyendo vehicle_config de session.json: {e}")

    # Sobrescribir con flags CLI si el usuario los especificó
    if args.max_rpm:
        vehicle_config["max_rpm"] = args.max_rpm
    if args.redline_rpm:
        vehicle_config["redline_rpm"] = args.redline_rpm
    if args.shift_rpm:
        vehicle_config["shift_light_rpm"] = args.shift_rpm

    try:
        process_video(video_file, csv_file, os.path.abspath(args.out), vehicle_config, rotate=args.rotate)
    finally:
        if clean_temp:
            shutil.rmtree(work_dir, ignore_errors=True)


if __name__ == "__main__":
    main()

