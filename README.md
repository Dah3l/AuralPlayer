<div align="center">
  <img src="assets/banner.jpg" width="1000" alt="AuralPlayer — El sonido, sin ruido">
  <h1>🎧 AuralPlayer</h1>
  <p><strong>Reproductor de música para Android — minimalista, elegante y sin cuentas. Tu música, sin ruido.</strong></p>
  <p>
    <a href="https://github.com/Dah3l/AuralPlayer/releases/latest">
      <img src="https://img.shields.io/badge/Descargar-Estable-37A866?style=for-the-badge&logo=android&logoColor=white" alt="Descargar la última versión estable">
    </a>
  </p>
  <p>
    <a href="https://github.com/Dah3l/AuralPlayer/releases">
      <img src="https://img.shields.io/github/downloads/Dah3l/AuralPlayer/total?style=for-the-badge&label=descargas%20totales&logo=github" alt="Descargas totales">
    </a>
  </p>
  <p>
    <a href="LICENSE"><img src="https://img.shields.io/badge/Licencia-GPLv3-blue.svg" alt="Licencia GPL v3"></a>
    <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat&logo=android&logoColor=white" alt="Android 8.0 o superior">
  </p>
</div>

AuralPlayer reproduce el audio local de tu dispositivo y descarga canciones de YouTube, todo con un diseño premium al estilo de la app web Aural y 16 temas para hacerlo tuyo. Sin nube, sin cuentas, sin rastreo: tu biblioteca vive en tu teléfono y nada más.

---

## 📸 Capturas

| Biblioteca | Reproduciendo ahora | Descargas de YouTube |
|---|---|---|
| <img src="assets/biblioteca.jpg" width="240"> | <img src="assets/reproductor.jpg" width="240"> | <img src="assets/descargas.jpg" width="240"> |

| Ecualizador | Identificar canción |
|---|---|
| <img src="assets/ecualizador.jpg" width="240"> | <img src="assets/shazam.jpg" width="240"> |

---

## ✨ Características

### 🏠 Inicio
- **Cabecera fija y búsqueda siempre visible** — nada se encoge ni desaparece al desplazar: el saludo, la búsqueda y los filtros están siempre a mano.
- **Saludo según la hora** en español, sin distracciones, junto al logo de Aural.
- **Filtros en pills** que siguen la pestaña activa al deslizar, sin saltos ni retrasos.
- **Más reproducidas** — la sección del inicio con tus 2 playlists que más suenan, en tarjetas con el botón ▶ dentro de la portada para empezar al instante; sin historial todavía, muestra Favoritas y Descargas.

### 🎵 Biblioteca
- 📂 **Escaneo de audio local** mediante `MediaStore`: título, artista, álbum, duración y carátula.
- 🗂️ **Pestañas**: Canciones · Artistas · Álbumes · Playlists · **Descargas** · **Carpetas**.
- 🔤 **Índice alfabético lateral** para saltar directo a una letra.
- 🔍 **Búsqueda local** instantánea por título, artista o álbum.
- 🔄 **Pull-to-refresh clásico** — tira hacia abajo para re-escanear tras añadir música nueva, con indicador de progreso.
- ❤️ **Favoritos** con un toque, persistidos entre sesiones, con reordenación por arrastre y su propio menú para quitar canciones.
- 🖼️ **Portada predeterminada** — las canciones sin carátula muestran el signo de música, nunca un hueco vacío.

### ▶️ Reproducción
- ⏯️ Play/pausa, siguiente/anterior y **seek** con slider premium.
- 🔁 **Ciclo de 4 estados** en un solo botón: sin repetir, repetir todo, repetir uno y aleatorio — el aleatorio mezcla la cola y la repite en bucle, para que la música no se detenga al final.
- ▶️ **El ▶ de cada playlist reproduce al instante** desde la primera canción, sin abrir la lista primero.
- 📋 **Cola de reproducción** visible, editable y **arrastrable** para reordenar.
- 🔇 **Reproducción sin pausas** (*gapless*) entre canciones.
- ⏱️ **Temporizador de suspensión** — la música se apaga sola.
- 🎚️ **Velocidad de reproducción** ajustable.
- 🌙 **Now Playing premium**: carátula grande con glow, título con marquesina, slider con thumb y sombra, controles elegantes y botones animados que reflejan su estado.

### 🎤 Letras sincronizadas (.lrc)
- 📝 **Soporte de archivos .lrc** — letras sincronizadas con la música.
- 🎵 **Línea actual integrada** en el Now Playing: se actualiza en tiempo real con la canción.
- 👆 Toca la letra para ver la **vista completa** con scroll automático, estilo Spotify: portada, título, artista y barra de progreso que avanza mientras suena.
- 📁 **"Agregar letra"** — elige cualquier .lrc de tu dispositivo y asócialo a la canción.
- 🌐 **"Buscar letra en línea"** — busca automáticamente en LRCLIB (gratis, sin API key) con campos editables de canción y artista.
- 🖼️ **Aural Cards** — desde la vista de letras, elige hasta 6 líneas y genera una tarjeta de 1440 px con portada, título, artista y los versos completos, sobre un degradado extraído de la propia portada y la onda de Aural como marca de agua. Compártela por la hoja del sistema (WhatsApp, Instagram, Telegram…) o **guárdala directo en tu galería**, en la carpeta Aural.

### ⬇️ Descargas de YouTube
- 🔴 **Búsqueda y descarga de audio** directo desde YouTube (solo audio, sin video), sin salir de la app.
- ⚡ **Descarga optimizada**: buffer de 64KB, headers de navegador, prioriza m4a.
- ❌ **Botón de cancelar** durante la descarga.
- 📥 Las canciones descargadas **aparecen automáticamente** en tu biblioteca.
- 📋 **Playlist "Descargas"** creada automáticamente — todo lo que bajas se organiza solo. Y si está vacía, su botón te lleva directo a buscar y descargar.
- 📱 **Compatible con Android 10, 11 y 12** — la descarga usa desugaring empaquetado, la misma solución de la app oficial de NewPipe.

### 🎙️ Identificar canción (estilo Shazam)
- 🎧 **Reconocimiento de música** con un toque: escucha lo que suena a tu alrededor y te dice qué es.
- ⬇️ Al identificarla, botón directo para **descargarla de YouTube** a tu biblioteca.
- 🎈 **Botón flotante** abajo a la derecha, con un anillo que pulsa suavemente — un toque y abre «Identificar canción».
- ⚡ Acceso rápido desde un **Tile de ajustes rápidos** de Android, sin abrir la app.

### 🎛️ Ecualizador y sonido
- 🎚️ **Ecualizador de 5 bandas** (60Hz · 230Hz · 910Hz · 3kHz · 14kHz) con presets (Normal, Classical, Dance…).
- 🔊 **Normalización de volumen** — iguala el volumen entre canciones.
- 📢 **Refuerzo de sonoridad** (*Loudness*) para auriculares y altavoces modestos.

### 🎨 Personalización
- 🌈 **16 temas de color** — 9 oscuros y 7 claros — con nombres propios y acento completo en toda la interfaz.
- 🖼️ **Selector de temas a pantalla completa**, con tarjetas tipo splash que muestran cada tema tal como se ve.
- 🖼️ **Portadas personalizadas**: busca carátulas en iTunes o elige una imagen de tu galería.
- ✂️ **Editor de recorte 1:1** — al elegir una foto para una playlist, ajusta el encuadre con cuadrícula (arrastrar y pellizcar) antes de guardarla; desde el menú ⋮ del detalle puedes agregar o eliminar la foto.
- ✏️ **Metadatos editables**: corrige título, artista y álbum de cualquier canción sin salir de la app.
- ✨ **Interfaz que responde al tacto** — cada tarjeta y botón tiene su efecto visual, y menús, hojas y diálogos heredan los colores completos del tema activo.

### 📋 Playlists
- ➕ Crear, renombrar y eliminar playlists.
- ➕/➖ Agregar canciones desde el menú de cada pista o con el selector múltiple.
- ⭐ Playlists fijas: **Favoritas** y **Descargas** en la sección "Tuyas".
- 💾 **Importar playlist y Exportar lista en .m3u** — respaldos y paso entre dispositivos. La playlist importada conserva el nombre de su archivo (y se numera si ya existe).
- 🗄️ Persistencia con **DataStore Preferences**.

### 🖼️ Detalle con imagen grande
- **Portada a lo ancho** al abrir cualquier playlist, álbum o artista, con el título encima.
- **Colapso animado** — al subir, la imagen se recoge dejando solo la barra con el título y la flecha de volver; el título gana peso y se desvanece al pasar el 75% del recorrido para no chocar con la barra.
- **Barra colapsada a tono**, un poco más alta y con la tonalidad del Now Playing, que consume sus propios toques para que ninguno caiga en la lista de detrás.

### 👆 Navegación
- **Swipe horizontal** entre pestañas de la biblioteca, además de tocar las pills.
- **Al volver atrás caes en la pestaña donde estabas**, y las listas conservan su posición (álbumes, artistas, playlists y vista de letra).
- **Doble confirmación al salir** — «Pulsa de nuevo para salir» — para que un toque accidental no cierre la música.

### 🔔 Segundo plano y sistema
- **Servicio en primer plano** con notificación multimedia (carátula, controles, progreso).
- **MediaSession**: control desde auriculares, Bluetooth y pantalla de bloqueo.
- La música sigue sonando al cerrar la app o bloquear el teléfono.
- **Aviso de nueva versión** — al abrir la app (máximo una vez al día), Aural consulta el último release público en GitHub y, si hay uno más nuevo, muestra un diálogo con «Descargar» y «Más tarde». En el menú ⋮, «Buscar actualizaciones» comprueba a mano. Sin backend ni permisos nuevos, y la versión de «Acerca de» se lee del build.
- **Onboarding** de bienvenida en el primer arranque.

---

## 🚀 Descarga

- **Estable:** [última Release de GitHub](https://github.com/Dah3l/AuralPlayer/releases/latest) — descarga el APK e instálalo (puede que Android pida permitir la instalación desde el navegador o el explorador de archivos).
- También se distribuye por **Telegram**.

---

## 🛠️ Stack tecnológico

| Componente | Versión |
|---|---|
| Lenguaje | Kotlin **2.0.21** (con el nuevo compilador de Compose) |
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

## 🔧 Comenzar (desarrollo)

### Requisitos
- **Android Studio** Ladybug o superior (o línea de comandos con JDK **17**).
- **Gradle 8.7** (wrapper recomendado) y un dispositivo/emulador con **Android 8.0 (API 26) o superior**.

### Construcción

```bash
# Clonar el repositorio
git clone https://github.com/Dah3l/AuralPlayer.git
cd AuralPlayer

# Generar el APK de depuración
./gradlew assembleDebug

# Instalar en un dispositivo conectado
./gradlew installDebug
```

El APK resultante se encuentra en `app/build/outputs/apk/debug/app-debug.apk`.

### Primer arranque
1. Otorga el permiso de **audio** cuando se solicite.
2. En Android 13+, otorga el permiso de **notificaciones** para ver la notificación multimedia.
3. Aural escanea automáticamente tu biblioteca y muestra las canciones. Desliza hacia abajo en la biblioteca para re-escanear tras añadir música nueva.

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
├── build.gradle.kts              # Plugins raíz (AGP 8.5.2, Kotlin 2.0.21)
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
        │       ├── theme/Theme.kt            # Temas de color M3
        │       ├── components/               # MiniPlayer y componentes comunes
        │       └── screens/
        │           ├── LibraryScreen.kt      # Pestañas + búsqueda
        │           ├── NowPlayingScreen.kt   # Carátula, controles y cola
        │           └── DetailScreen.kt       # Álbum, artista, playlist, favoritas
        └── res/                    # Iconos, colores y recursos del launcher
```

---

## 🎨 Diseño

- **Diseño premium**: fondo casi negro por defecto, superficies escalonadas y un color de acento por tema.
- **16 temas** integrados para personalizar el acento (el turquesa es el de la casa).
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
6. **Actualizaciones**: al abrir la app, se consulta la API pública de releases de GitHub y se compara con la versión del build; sin backend, sin cuentas y sin permisos nuevos.

---

## 🐞 Solución de problemas

| Problema | Solución |
|---|---|
| No aparecen canciones | Otorga el permiso de audio y desliza hacia abajo para re-escanear. Asegúrate de que el archivo esté en un volumen visible para MediaStore. |
| No suena en segundo plano | Verifica el permiso de notificaciones (Android 13+) y que el ahorro de batería no detenga el servicio. |
| Sin carátula | Algunos MP3 no incrustan portada; Aural usa `ContentUris` de MediaStore y recurre a un marcador gráfico. |
| Controles de auriculares no responden | La app debe estar conectada al `MediaSession`; reinicia la reproducción si el servicio fue destruido por el sistema. |

---

## 📋 Hoja de ruta

- [ ] Widget en pantalla de inicio
- [ ] Música en línea (streaming)
- [ ] Android Auto

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

Este proyecto está bajo la **GNU General Public License v3.0** — ver el archivo [LICENSE](LICENSE) para más detalles.

Copyright (C) 2026 Dahel

---

Hecho con ❤️ y Jetpack Compose. Si te gusta, dale ⭐ al repositorio.
