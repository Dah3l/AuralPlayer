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

## Estado actual (2026-11-14)

- **v1.1.0 (versionCode 11): construida, verificada por Dahel y lista.**
  Pendiente: Dahel dispara `release.yml` con `v1.1.0` y pega el What's
  New; luego sube a Apklis. La 1.1.0 sale SOLO GRATIS.
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
