# 🛠️ Guía de Instalación y Compilación de Tachyon

Este documento detalla los pasos para instalar el APK precompilado de Tachyon o para compilar el proyecto desde el código fuente tanto para la aplicación Android como para las herramientas de escritorio en Python.

---

## 📱 1. Instalación Rápida del APK

Si no deseas compilar el código fuente y solo quieres utilizar la aplicación en tu móvil:

### Opción A: Descarga Directa en el Dispositivo
1. Descarga el paquete APK directamente a tu teléfono:
   👉 [**Descargar `apk/Tachyon.apk`**](apk/Tachyon.apk)
2. Abre la descarga e instala la aplicación (habilita la opción *"Permitir instalar aplicaciones de orígenes desconocidos"* en los ajustes de tu navegador si el sistema lo solicita).
3. Concede los permisos solicitados al abrir la aplicación:
   - **Cámara:** para la grabación de vídeo FHD sincronizado.
   - **Ubicación Precisa:** para el posicionamiento GPS de alta fidelidad.
   - **Dispositivos Cercanos / Bluetooth:** para la comunicación con el escáner OBD-II BLE.

### Opción B: Instalación mediante ADB (Cable USB)
Conecta tu dispositivo Android al PC con la **Depuración por USB** activada en las *Opciones para desarrolladores*:

```bash
# Verificar que el dispositivo está detectado
adb devices

# Instalar o actualizar la aplicación conservando los datos existentes
adb install -r apk/Tachyon.apk
```

---

## 🏗️ 2. Compilación de la Aplicación Android desde el Código Fuente

### Requisitos Previos
* **Java Development Kit:** JDK 17 LTS (OpenJDK o Amazon Corretto recomendados).
* **Android SDK:** API Level 34 (Android 14) con *Build Tools 34.0.0+*.
* **Git:** Para clonar el repositorio.
* **Android Studio:** Ladybug (2024.2+) o superior (opcional, también se puede compilar por línea de comandos).

### Paso 1: Clonar el Repositorio
```bash
git clone https://github.com/GuacamoleGH/Tachyon.git
cd Tachyon
```

### Paso 2: Verificar la Configuración del Entorno
Asegúrate de que la variable de entorno `JAVA_HOME` apunte a tu instalación de JDK 17 y que `ANDROID_HOME` apunte a tu directorio de Android SDK.

En Windows (PowerShell):
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```

En Linux / macOS:
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME=$HOME/Android/Sdk
```

### Paso 3: Ejecutar Pruebas Unitarias
El proyecto cuenta con suites de test unitario para verificar la lógica inercial, cálculos de aceleración y parseo de tramas ELM327:

```bash
# En Linux / macOS
./gradlew testDebugUnitTest

# En Windows (PowerShell / CMD)
.\gradlew.bat testDebugUnitTest
```

### Paso 4: Compilar el APK de Depuración (Debug)
```bash
# Compilar APK Debug
./gradlew assembleDebug
```
El archivo generado se ubicará en:
`app/build/outputs/apk/debug/app-debug.apk`

### Paso 5: Compilar e Instalar Directamente en el Móvil
Si tienes el teléfono conectado con depuración USB:
```bash
./gradlew installDebug
```

---

## 🐍 3. Configuración de las Herramientas de PC (`pc_tools`)

Tachyon incluye un renderizador en Python (`render_telemetry_overlay.py`) para generar vídeos de alta resolución con el cuadro de mandos deportivo superpuesto a partir de las sesiones exportadas en `.zip`.

### Requisitos
* **Python 3.10** o superior.
* **FFmpeg** instalado en el sistema (recomendado para procesamiento multimedia).

### Instalación de Dependencias
```bash
# 1. Entrar en la carpeta de herramientas
cd pc_tools

# 2. (Opcional pero recomendado) Crear entorno virtual
python -m venv venv

# Activar entorno virtual
# En Windows:
.\venv\Scripts\activate
# En Linux / macOS:
source venv/bin/activate

# 3. Instalar dependencias requeridas (OpenCV, NumPy, etc.)
pip install -r requirements.txt
```

### Uso del Renderizador de Telemetría
```bash
# Sintaxis básica
python render_telemetry_overlay.py --zip ruta/a/tu/sesion_bundle.zip --out video_telemetria.mp4

# Opciones avanzadas:
# Girar 180° si el teléfono iba montado invertido en el salpicadero
python render_telemetry_overlay.py --zip sesion.zip --out salida.mp4 --rotate 180

# Previsualizar sin re-codificar todo el metraje
python render_telemetry_overlay.py --zip sesion.zip --preview
```

---

## 🔍 4. Solución de Problemas Frecuentes

| Problema | Causa Posible | Solución |
| :--- | :--- | :--- |
| `Unsupported class file major version` | Versión incorrecta de Java | Configura `JAVA_HOME` a JDK 17 exactamente. Gradle 8.x no soporta JDKs antiguos ni versiones preliminares. |
| `Device unauthorized` en ADB | Falta confirmación de clave RSA | Desbloquea la pantalla de tu móvil y acepta el diálogo *"¿Permitir depuración por USB?"*. |
| `No matching client found for package` | Caché residual de Gradle | Ejecuta `./gradlew clean` antes de volver a compilar. |
| Fallo al escanear BLE en Android 12+ | Permisos de Bluetooth denegados | Asegúrate de conceder el permiso de *Dispositivos Cercanos* y tener el GPS activo en el panel rápido del móvil. |
