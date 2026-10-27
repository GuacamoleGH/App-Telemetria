# 🔐 Guía de Firma de Releases y Gestión de Keystore

Este documento detalla los estándares de seguridad, la política de versionado (`versionCode` y `versionName`), la configuración de claves de firma para producción y el procedimiento para purgar archivos binarios del historial de Git.

---

## ⚠️ ADVERTENCIA CRÍTICA SOBRE EL KEYSTORE EN ANDROID

> [!CAUTION]
> **NUNCA PIERDAS EL KEYSTORE NI SUS CONTRASEÑAS.**
> En el ecosistema de Android, la identidad criptográfica de la aplicación está vinculada permanentemente a la clave privada del keystore.
> - Si pierdes el archivo `.jks` / `.keystore` o sus contraseñas, **será matemáticamente imposible publicar actualizaciones compatibles** para los usuarios que ya tengan la aplicación instalada.
> - El sistema operativo Android rechazará cualquier instalación sobre una versión previa si el certificado de firma no es idéntico (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
> - **NUNCA subas el archivo keystore ni sus contraseñas al repositorio de Git.** Mantén una copia de seguridad externa segura (en un gestor de contraseñas o almacenamiento cifrado fuera de línea).

---

## 🛠️ 1. Cómo Generar el Keystore de Producción

Para crear un nuevo almacén de claves de firma de producción, ejecuta el siguiente comando desde una terminal con el JDK 17+ instalado:

```bash
keytool -genkeypair -v -keystore tachyon-release.jks -alias tachyon -keyalg RSA -keysize 2048 -validity 10000
```

El asistente te solicitará:
1. **Contraseña del keystore** (mínimo 6 caracteres, recomendada clave robusta aleatoria).
2. **Nombre y datos del desarrollador u organización.**
3. **Contraseña del alias** (puede ser la misma del keystore).

Guarda el archivo resultante `tachyon-release.jks` en una ubicación segura **fuera del repositorio Git**.

---

## ⚙️ 2. Configuración Segura de Firma en el Proyecto

El archivo `app/build.gradle.kts` está configurado para leer las credenciales sin exponerlas al control de versiones. Puedes configurarlas mediante **`local.properties`** o mediante **variables de entorno del sistema**.

### Opción A: Archivo `local.properties` (Recomendada en entorno local)
Añade las siguientes líneas a tu archivo `Tachyon/local.properties` (este archivo ya está ignorado en `.gitignore`):

```properties
# Configuración de firma de Release Tachyon
release.keystore.file=C:/ruta/segura/a/tachyon-release.jks
release.keystore.password=TuContrasenaDeKeystore
release.key.alias=tachyon
release.key.password=TuContrasenaDeAlias

# Política de versión (opcional, por defecto administrado en build.gradle.kts)
app.versionCode=2
app.versionName=1.1.0
```

### Opción B: Variables de Entorno (Recomendada para CI/CD como GitHub Actions)
Configura las siguientes variables en tu sistema o en los secrets de tu runner:
* `RELEASE_KEYSTORE_FILE`: Ruta absoluta al archivo `.jks`.
* `RELEASE_KEYSTORE_PASSWORD`: Contraseña del keystore.
* `RELEASE_KEY_ALIAS`: Nombre del alias de la clave.
* `RELEASE_KEY_PASSWORD`: Contraseña de la clave privada.

> [!NOTE]
> Si estas propiedades no están definidas en el entorno, `./gradlew assembleRelease` compilará el APK sin firmar (unsigned) sin romper la compilación.

---

## 📈 3. Política de Versionado (`versionCode` y `versionName`)

1. **`versionCode` (Entero positivo estrictamente incremental):**
   - Debe incrementarse en **+1 con cada release** publicada.
   - Android prohíbe instalar una versión con un `versionCode` inferior o igual al ya instalado.
   - Historial de versiones del proyecto:
     - `versionCode = 1`: Tachyon v1.0.0 (Release inicial oficial)
     - `versionCode = 2`: Tachyon v1.1.0 (Consumo, rutas, recorte de privacidad y STORED video)
2. **`versionName` (Cadena Semver `X.Y.Z`):**
   - Identificador visible para los usuarios y en los metadatos de telemetría `session.json`.

---

## 🚀 4. Cómo Compilar la Release Firmada

Una vez configurado `local.properties` con tu keystore:

```bash
cd Tachyon
./gradlew assembleRelease
```

El APK firmado y optimizado se generará en:
`app/build/outputs/apk/release/app-release.apk`

---

## 🧹 5. Procedimiento para Purgar Binarios Históricos de Git (`git-filter-repo`)

Los archivos binarios (`.apk`) ya han sido eliminados del árbol de trabajo actual y excluidos en `.gitignore`. Sin embargo, los commits antiguos del historial aún almacenan los binarios previos en el árbol de Git.

> [!WARNING]
> **ATENCIÓN:** Reescribir el historial de Git cambia los hashes SHA-1 de todos los commits posteriores y requiere forzar el empuje (`git push --force`). Solo debes ejecutar este comando cuando ningún otro colaborador esté trabajando sobre la rama.

Para purgar definitivamente el APK de todo el historial de Git utilizando la herramienta oficial recomendada por Git ([`git-filter-repo`](https://github.com/newren/git-filter-repo)):

```bash
# 1. Instalar git-filter-repo si no lo tienes (vía pip)
pip install git-filter-repo

# 2. Situarse en la raíz del repositorio
cd "c:\Proyectos VSC\Telemetria\Tachyon"

# 3. Eliminar completamente cualquier referencia al archivo apk/Tachyon.apk de todos los commits
git filter-repo --path apk/Tachyon.apk --invert-paths --force

# 4. Forzar la actualización en GitHub
git push origin --force --all
git push origin --force --tags
```
