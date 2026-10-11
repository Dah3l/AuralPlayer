# 🎧 Aural

**Reproductor de música para Android con diseño moderno y minimalista (tema oscuro, limpio, al estilo de la app web Aural), pero con todas las funciones de un reproductor convencional.**

Aural reproduce el audio local del dispositivo —sin nube, sin cuentas, sin rastreo— escaneando la biblioteca con `MediaStore` y reproduciéndola con `Media3 (ExoPlayer)` en segundo plano, con notificación multimedia y controles para auriculares y Bluetooth.

---

## ✨ Características

### Biblioteca
- 📂 **Escaneo de audio local** mediante `MediaStore`: título, artista, álbum, duración, número de pista y carátula del álbum.
- 🗂️ **Cuatro pestañas**: Canciones · Artistas · Álbumes · Playlists.
- 🔍 **Búsqueda local** instantánea sobre toda la biblioteca (título, artista o álbum).
- ❤️ **Canción favorita** con un toque, persistida entre sesiones.

### Reproducción
- ▶️ Play / pausa, siguiente / anterior y **seek** con barra deslizante.
- 🔀 **Aleatorio** (shuffle) y 🔁 **repetición** (desactivada / todo / una).
- 📋 **Cola de reproducción** visible y editable: reproducir cualquier pista de la cola o quitarla.
- ⏸️ Añadir canciones a la cola desde los menús de la biblioteca.
- 🌙 Pantalla **"Now Playing"** con carátula grande, controles completos y cola integrada.
- 📱 **Mini-reproductor persistente** en la parte inferior; tocarlo abre "Now Playing".

### Segundo plano y sistema
- 🔔 **Servicio en primer plano** (`MediaSessionService`) con **notificación multimedia** (carátula, controles, barra de progreso).
- 🎧 **MediaSession**: control desde auriculares cableados, Bluetooth y pantallas de bloqueo.
- La reproducción continúa al cerrar la app o bloquear el teléfono.

### Playlists
- ➕ Crear y eliminar playlists personalizadas.
- ➕/➖ Agregar y quitar canciones desde el menú contextual de cada pista.
- ▶️ Reproducir una playlist completa (con o sin aleatorio).
- 💾 Persistencia con **DataStore Preferences** (formato JSON interno).

---

## 🛠️ Stack tecnológico

| Componente | Versión |
|---|---|
| Lenguaje | Kotlin **1.9.24** |
| Java / JVM target | **17** |
| Gradle | **8.7** |
| Android Gradle Plugin | **8.5.2** |
| SDK | minSdk **26** · targetSdk **34** · compileSdk **34** |
| UI | Jetpack Compose + **Material 3** (BOM `2024.09.02`) |
| Audio | **Media3 / ExoPlayer** `1.4.1` (exoplayer, session, common) |
| Persistencia de ajustes | DataStore Preferences **1.1.1** |
| Asincronía | Coroutines **1.8.1** (+ `kotlinx-coroutines-guava` para `await()` en MediaController) |
| Imágenes | Coil Compose 2.7.0 (carga de carátulas desde MediaStore) |

Arquitectura sencilla y sin librerías de DI externas: un contenedor de dependencias manual en `AuralApp` (Application) conecta Repositorio → ViewModel → Compose, y el `PlaybackManager` actúa como puente con el servicio Media3 vía `MediaController`.

---

## 📱 Capturas de pantalla

> _Pendiente de añadir._ Sugerencia: colocar las imágenes en `docs/screenshots/` y descomentar las líneas de abajo.

<!--
| Biblioteca | Now Playing | Cola |
|---|---|---|
| ![](docs/screenshots/library.png) | ![](docs/screenshots/nowplaying.png) | ![](docs/screenshots/queue.png) |
-->

---

## 🚀 Comenzar

### Requisitos
- **Android Studio** Ladybug o superior (o línea de comandos con JDK **17**).
- **Gradle 8.7** (wrapper recomendado) y un dispositivo/emulador con **Android 8.0 (API 26) o superior**.

### Construcción

```bash
# Clonar el repositorio
git clone https://github.com/<tu-usuario>/aural.git
cd aural

# Generar el APK de depuración
./gradlew assembleDebug

# Instalar en un dispositivo conectado
./gradlew installDebug
```

El APK resultante se encuentra en `app/build/outputs/apk/debug/app-debug.apk`.

### Primer arranque
1. Otorga el permiso de **audio** cuando se solicite.
2. En Android 13+, otorga el permiso de **notificaciones** para ver la notificación multimedia.
3. Aural escanea automáticamente tu biblioteca y muestra las canciones. Usa el botón **↻** para volver a escanear tras añadir música nueva.

---

## 🔐 Permisos

| Permiso | Cuándo | Para qué |
|---|---|---|
| `READ_MEDIA_AUDIO` | Android 13+ (API 33+) | Leer los archivos de audio del dispositivo |
| `READ_EXTERNAL_STORAGE` (maxSdk 32) | Android ≤ 12 | Leer los archivos de audio en versiones antiguas |
| `POST_NOTIFICATIONS` | Android 13+ | Mostrar la notificación multimedia en curso |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Siempre | Servicio en primer plano de reproducción |
| `WAKE_LOCK` | Siempre | Mantener la reproducción con pantalla apagada |

Los permisos de usuario se solicitan de forma dinámica en `MainActivity` con `ActivityResultContracts`.

---

## 📁 Estructura del proyecto

```
Aural/
├── build.gradle.kts              # Plugins raíz (AGP 8.5.2, Kotlin 1.9.24)
├── settings.gradle.kts           # Repositorios y módulo :app
├── gradle.properties             # Ajustes globales de Gradle
└── app/
    ├── build.gradle.kts          # Dependencias de Compose, Media3, DataStore…
    └── src/main/
        ├── AndroidManifest.xml   # Permisos, actividad y MusicService
        ├── java/com/aural/player/
        │   ├── AuralApp.kt           # Application: contenedor de dependencias
        │   ├── MainActivity.kt       # Permisos, navegación y mini-reproductor
        │   ├── AuralViewModel.kt     # Estado de biblioteca, búsqueda, playlists
        │   ├── data/
        │   │   ├── MusicModels.kt    # Song, Artist, Album, Playlist
        │   │   ├── MusicRepository.kt# Escaneo MediaStore + agrupaciones
        │   │   └── SettingsStore.kt  # Playlists y favoritos (DataStore + JSON)
        │   ├── playback/
        │   │   ├── MusicService.kt   # MediaSessionService + notificación
        │   │   └── PlaybackManager.kt# MediaController ↔ estado Flow
        │   └── ui/
        │       ├── theme/Theme.kt            # Tema oscuro minimalista M3
        │       ├── components/               # MiniPlayer y componentes comunes
        │       └── screens/
        │           ├── LibraryScreen.kt      # Pestañas + búsqueda
        │           ├── NowPlayingScreen.kt   # Carátula, controles y cola
        │           └── DetailScreen.kt       # Álbum, artista, playlist, favoritas
        └── res/                    # Iconos, colores y recursos del launcher
```

---

## 🎨 Diseño

- **Tema estrictamente oscuro**: fondo casi negro (`#0E0F13`), superficies escalonadas y un único color de acento verde-turquesa (`#2ED3B7`).
- Tipografía limpia (sans system con pesos definidos), iconografía redondeada Material Symbols y espacios generosos.
- Sin saturación visual: jerarquía por contraste y peso tipográfico, no por color.
- Bordes suaves, chips discretos y divisores sutiles para mantener la calma de la interfaz.

---

## 🧠 Cómo funciona

1. **Escaneo**: `MusicRepository` consulta `MediaStore.Audio` en `Dispatchers.IO` y agrupa por artista/álbum.
2. **Estado**: `AuralViewModel` expone `StateFlow`s (`library`, `playlists`, `favorites`, `playerState`) que observa Compose.
3. **Reproducción**: `MusicService` aloja un `ExoPlayer` dentro de una `MediaSession`; `PlaybackManager` se conecta como `MediaController` y traduce los eventos del reproductor a un `PlayerUiState` inmutable (incluye un ticker de posición ~500 ms).
4. **Persistencia**: playlists y favoritos se serializan a JSON en una única clave de DataStore Preferences.
5. **Notificación**: la genera automáticamente el `MediaNotificationManager` de Media3 a partir de los metadatos del `MediaItem` (carátula incluida).

---

## 🐞 Solución de problemas

| Problema | Solución |
|---|---|
| No aparecen canciones | Otorga el permiso de audio y pulsa **↻** (reescanear). Asegúrate de que el archivo esté en un volumen visible para MediaStore. |
| No suena en segundo plano | Verifica el permiso de notificaciones (Android 13+) y que el ahorro de batería no detenga el servicio. |
| Sin carátula | Algunos MP3 no incrustan portada; Aural usa `ContentUris` de MediaStore y recurre a un marcador gráfico. |
| Controles de auriculares no responden | La app debe estar conectada al `MediaSession`; reinicia la reproducción si el servicio fue destruido por el sistema. |

---

## 📋 Hoja de ruta

- [ ] Ecualizador y normalización de volumen
- [ ] Ordenación personalizada de la cola (arrastrar y soltar)
- [ ] Importar/exportar playlists
- [ ] Soporte para carpetas y reproducción gapless
- [ ] Widget en pantalla de inicio y Android Auto

---

## 🤝 Contribuir

Las contribuciones son bienvenidas:

1. Haz *fork* del repositorio.
2. Crea una rama de característica: `git checkout -b feature/algo-nuevo`.
3. Confirma tus cambios: `git commit -m "feat: añade algo nuevo"`.
4. Envía la rama: `git push origin feature/algo-nuevo`.
5. Abre un *Pull Request* describiendo el problema y la solución.

Por favor, escribe comentarios de código en español y sigue el estilo existente (Compose funcional + Flows, sin DI externa).

---

## 📄 Licencia

Proyecto de ejemplo — distribúyelo bajo la licencia que prefieras (MIT recomendada si planeas publicarlo). El nombre "Aural" es un homenaje estético a la app web homónima; este proyecto no está afiliado oficialmente.

---

Hecho con ❤️ y Jetpack Compose. Si te gusta, dale ⭐ al repositorio.
