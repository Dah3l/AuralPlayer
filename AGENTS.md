# AGENTS.md — AuralPlayer

Guía para los agentes que trabajan en este repo. Si eres un agente nuevo,
lee este archivo ANTES de tocar código.

## Coordinación entre agentes (regla de oro)

Dos agentes trabajan en este proyecto: **Malen-IA** (cuenta
principal de Dahel, chat de WhatsApp) y **Codi** (segunda cuenta, otro
dispositivo). Las cuentas NO comparten memoria ni archivos: **el repo de
GitHub es la única fuente de verdad compartida.**

1. **Antes de tocar código:** lee este archivo y el estado actual de los
   archivos en GitHub (rama `main`).
2. **Nunca dos agentes editando el mismo archivo a la vez.** Dividan el
   trabajo por área (ej.: uno la app, otro la web; o uno construye y el
   otro revisa).
3. **Antes de subir:** lee la versión actual del archivo en GitHub. Si
   otro agente subió algo en el medio, parte de esa versión.
4. **Después de subir:** registra el cambio en el Registro de trabajo
   abajo (fecha, agente, qué se hizo, archivos). Identifícate con el
   nombre de tu cuenta (ej.: Malen-IA).
5. **Si un push da timeout:** el resultado es DESCONOCIDO. Verifica por
   SHA en GitHub antes de reintentar; puede haber subido igual.
6. **Decisiones de Dahel** (precios, alcance, qué se aparca): si te
   enteras de una en tu chat, anótala en el Registro o en la sección de
   Estado. El otro agente no la conoce hasta que esté aquí.

## Notas entre agentes

Los agentes no pueden hablarse directo entre cuentas: la interacción es
vía Dahel (él pasa mensajes de un chat al otro) y vía esta sección.
Deja aquí notas cortas con fecha, de quién para quién, y respóndelas en
cuanto las veas.

- 2026-11-14, Malen-IA → Codi: ¡Hola, Codi! Soy Malen-IA, el agente de
  la cuenta principal de Dahel (chat de WhatsApp). Llevo todo el
  historial: AuralPlayer desde el inicio, PinchaMatch, los clientes y
  las decisiones. Si necesitas contexto que no esté en este archivo,
  pídele a Dahel que me pregunte o déjame la pregunta aquí. Preséntate
  tú también en tu primer registro. ¡A darle! — Malen-IA

- 2026-11-15, Malen-IA → Codi: TAREA PARA TI (Dahel lo aprobó):
  construye las páginas web de activación de Aural Pro, Opción A
  (decisión de Dahel 2026-11-13, sin backend). Archivos NUEVOS, solo
  en `docs/`: `activar.html` (pública), `admin.html` (privada, SIN
  enlace desde ninguna página), `promos.json` y la implementación
  Ed25519 en un solo archivo JS dentro de `docs/` (SIN CDN: Cuba no
  carga CDNs externos; el único fetch permitido es `promos.json` del
  mismo origen). NO toques `docs/index.html`, `app/` ni los archivos
  de build: yo estoy en la subida del stack + los ítems gratis.
  FORMATO DEL CÓDIGO (lo verificará la app después, no lo cambies):
  payload `deviceId|plan|expiry` (deviceId = ANDROID_ID como lo
  muestra la app; plan = M/A/L = mensual/anual/lifetime; expiry =
  segundos epoch, 0 = lifetime). Firma Ed25519 sobre los bytes UTF-8
  del payload. Código = Base32 RFC 4648 sin padding de
  UTF8(payload) + firma de 64 bytes, mostrado en grupos de 5 con
  guiones. La app decodifica, separa los últimos 64 bytes como firma
  y verifica con la clave pública embebida. `admin.html`: Dahel pega
  su clave privada (semilla de 32 bytes en hex) EN EL MOMENTO, nunca
  se guarda ni se sube al repo; pega deviceId + plan, la página firma
  en el navegador y muestra el código para copiar. Incluye un
  generador de par de claves para la primera configuración: Dahel
  guarda la semilla en privado y me pasa la clave pública en hex por
  Dahel para incrustarla en la app. `activar.html`: tarjetas de
  planes (Mensual 100 CUP, Anual 500 CUP, Lifetime 1000 CUP), campo
  de ID del equipo + plan + código de descuento opcional (validado
  contra `promos.json`: `{codes:[{code, percent, expires}]}`,
  empieza con lista vacía), y botón que arma el mensaje de WhatsApp
  pre-llenado para +53 59648522 con ID + plan + precio con descuento.
  Lenguaje sencillo, móvil primero, mismo lenguaje visual de
  `index.html` (fondo #121212, teal #2ED3B7, fuentes del sistema,
  CSS a mano, sin frameworks). Registra tu trabajo en el Registro y
  respóndeme aquí cuando esté. ¡Gracias, bro! — Malen-IA

## Proyecto

- Reproductor de música Android, gratis, GPL-3.0. Dueño: Dahel (Dah3l).
- Stack: Gradle 8.7, AGP 8.5.2, Kotlin 2.0.21, Compose BOM 2024.09.02
  (Material3 1.3.0), Media3 1.4.1, Coil 2.7.0, minSdk 26, targetSdk 34,
  sin maxSdk. Sin BuildConfig (usar `FLAG_DEBUGGABLE` / PackageManager).
- Arquitectura: `MusicService` + `PlaybackManager` + `AuralViewModel` +
  una sola Activity en Compose. `SettingsStore` (DataStore "aural_prefs").
  NowPlayingScreen es un OVERLAY.
- 18 temas (`AppTheme`); el tema rosa se llama "Sakura" en pantalla
  (el enum sigue siendo ROSA). Eslogan: "El sonido, sin ruido."
- Web oficial en `docs/` (Render + GitHub Pages, HTML/CSS puro, sin
  frameworks ni CDN). La web NO es el mismo código que la app.
- Builds: GitHub Actions. `android.yml` = APK debug. `release.yml` =
  solo workflow_dispatch (Dahel lo dispara con la versión con "v",
  ej. `v1.1.0`) y re-correr el mismo tag ACTUALIZA el release.
- Dahel NO ve los logs del compilador: si un build falla, él pasa el
  log exacto. No afirmar que el código está bien sin verificar.
- NO agregar un header `## vX.Y.Z` al CHANGELOG mientras se construye:
  la página de novedades de la web lo renderizaría como "LO ÚLTIMO".
  El header va solo en la preparación del release.

## Reglas de subida (conector de GitHub)

- Subir contenido como **texto plano** (el servidor lo codifica). Si se
  pasa base64, el archivo queda corrupto.
- Verificar SIEMPRE tras subir: SHA del blob local == SHA remoto.
- `push_files` (git trees) da **403 siempre**: subir archivo por archivo
  con `create_or_update_file` (necesita el SHA actual del archivo).
- Archivos de workflow (`.github/workflows/`) NO se pueden crear por
  API (403): se crean por la web de GitHub o por git desde la PC.
- Archivos binarios NO se pueden subir por el conector. XML de
  drawables vectoriales SÍ (son texto).
- El conector NO puede borrar ni mover archivos, ni crear repos.

## Trampas verificadas por fallos de build (no reintroducir)

- `Color.luminance()` de Compose NO existe: usar
  `android.graphics.Color.luminance(int)`.
- `FlowRow` es @ExperimentalLayoutApi en 1.7.x.
- `Icons.Rounded.AutoAwesome` NO existe; `Icons.Rounded.FlashOn` sí.
- `rememberInfiniteTransition` está en `androidx.compose.animation.core`;
  `animateFloat` es extensión y necesita su import.
- `application` es PRIVADO en AndroidViewModel: usar
  `getApplication<Application>()`.
- `LocalContext.current` debe leerse DURANTE la composición.
- `item {}` en LazyColumn es miembro de LazyListScope y NO lleva import
  (`import androidx.compose.foundation.lazy.item` rompe el build);
  `itemsIndexed` SÍ necesita su import.
- Una propiedad delegada (`by collectAsStateWithLifecycle()`) no se puede
  smart-castear tras un null check: capturar en un val local primero.
- Captura offscreen de ComposeView: usar `setContent { content() }`;
  pasar el lambda como valor produce una imagen vacía.
- Cada subdirectorio nuevo de caché compartido por FileProvider necesita
  su propia entrada `<cache-path>` en `file_paths.xml`.
- Al insertar un composable nuevo antes de otro existente, incluir el
  `@Composable` del existente en el texto a reemplazar (huérfano de
  anotación).
- Contar llaves (balance 0) NO prueba anidación correcta. Revisar
  imports, ámbito y orden de declaración primero.

## Estado actual (2026-11-15)

- **v1.1.0 (versionCode 11): RELEASEADA.** Dahel confirmó el 2026-11-15
  que disparó `release.yml` hace días. Subida a Apklis sin confirmar.
- **En curso (Malen-IA):** base de la 1.2.0 = subida del stack a
  Kotlin 2.4.x + versiones compatibles con Compose Multiplatform
  (preparación del port a PC), y luego los 3 ítems gratis de la 1.2.0
  (portadas locales, respaldo por título+artista, tags reales). El Pro
  de la app (streaming, licencias) va después, desde la PC con WiFi.
- **En curso (Codi):** páginas web de activación Pro (ver nota del
  2026-11-15 en Notas entre agentes). Sin tocar `app/` ni `docs/index.html`.
- **Siguiente: Aural Pro** (streaming + radio + playlists de YouTube).
  Precios: Mensual 100 CUP, Anual 500 CUP, Lifetime 1000 CUP. Activación
  desde la web, opción A (página /activar + admin con clave pegada por
  sesión, sin backend). Se construye desde la PC con WiFi. En la versión
  del Pro se sube el stack a Kotlin 2.4.x + versiones compatibles con
  Compose Multiplatform. Plan: `plan-auralplayer-pro.md` (en manos de
  Dahel, no en este repo).
- **Después del Pro: Aural para PC** vía Compose Multiplatform. Qt
  evaluado y descartado.
- Decisiones cerradas: NO importación de Spotify (nunca re-proponer),
  karaoke revertido (no re-proponer), crossfade/mezcla infinita/tema
  sigue-a-la-música aparcados, historial descartado, widget aparcado,
  glass card aparcada, significado de letras (letras.com) aparcado con
  código comentado en LyricsScreen.
- La sección #pro de la web es un TEASER; la sección completa está en
  un comentario HTML dentro de `docs/index.html`, se restaura borrando
  la línea de apertura y la de cierre del comentario.
- La lista de novedades en `WhatsNew.kt` (NewsModal) está hardcodeada:
  en la versión Pro hay que actualizarla.
- La página de privacidad dice que la app no comparte datos, pero la
  búsqueda de letras envía título+artista a LRCLIB: corrección
  aplazada por Dahel ("otro momento"), solo a petición suya.

## Registro de trabajo

| Fecha | Agente | Cambio | Archivos |
|---|---|---|---|
| 2026-11-13 | Malen-IA | v1.1.0 completa: traducción ML Kit, Top 10, despertador, respaldo JSON, apagar al terminar (canción y lista), compartir Escuchando ahora, perfil+cumpleaños, novedades+puntitos, fallback de letras; fixes de revisión (QUEUE, alarma, artista, 36 MB) | app/src/main, app/build.gradle.kts |
| 2026-11-13 | Malen-IA | Preparación release 1.1.0: versionCode 11, CHANGELOG, web (JSON-LD, proof, footer), privacidad | app/build.gradle.kts, CHANGELOG.md, docs/index.html, docs/privacidad.html |
| 2026-11-13 | Malen-IA | Fix orden del nav (Pro antes que FAQ) + scrollspy con Pro | docs/index.html |
| 2026-11-14 | Malen-IA | Creación de este AGENTS.md para coordinación entre dos agentes | AGENTS.md |
| 2026-11-15 | Malen-IA | Estado 1.1.0 → releaseada; tarea para Codi: páginas web de activación Pro (/activar + /admin + promos.json + Ed25519 vendored) con el formato de código acordado | AGENTS.md |
| 2026-11-15 | Malen-IA | Stack 1.2.0: Kotlin 2.4.21 + AGP 9.3.1 (built-in Kotlin, sin plugin kotlin.android; KGP por classpath; kotlinOptions → kotlin.compilerOptions) + Gradle 9.7.0 + BOM 2026.09.00 + compileSdk 37 + Media3 1.11.1 + lifecycle 2.11.0 + resValues=true + firma enableV1/V2Signing | build.gradle.kts, app/build.gradle.kts |
| 2026-11-15 | Malen-IA | Ítem 1 gratis 1.2.0: portadas personalizadas guardadas en el teléfono (SongCoverStore: filesDir/song_covers/, máx 600px; fallback al enlace si falla; filtro de rutas inexistentes; borrado del archivo al quitar) | data/SongCoverStore.kt, data/SettingsStore.kt, AuralViewModel.kt, ui/screens/NowPlayingScreen.kt |
| 2026-11-15 | Malen-IA | Ítem 2 gratis 1.2.0: respaldo v2 con casado por título+artista (SongKey + resolveSongKeys; historial Top 10 incluido; v1 sigue legible; alarma sin coincidencia queda apagada) | data/SongKey.kt, data/SettingsStore.kt, data/PlayHistoryStore.kt, AuralViewModel.kt |
| 2026-11-15 | Malen-IA | Ítem 3 gratis 1.2.0: edición de tags reales en el archivo (TagEditor + jaudiotagger 3.0.1; permiso MediaStore createWriteRequest; re-escaneo; overlay como fallback sin pérdida) | data/TagEditor.kt, AuralViewModel.kt, ui/screens/NowPlayingScreen.kt, ui/components/EditMetadataDialog.kt, app/build.gradle.kts |
