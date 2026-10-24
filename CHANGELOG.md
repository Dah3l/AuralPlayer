# Historial de cambios — AuralPlayer

Todas las novedades de cada versión del APK, de la más reciente a la primera.
Este archivo es la constancia oficial de cambios; las notas de cada release
en GitHub salen de aquí.

## v1.0.3 — 2026-10-23

### Novedades
- **Tema nuevo: Nightly Sakura** — el rosa de Sakura en modo oscuro, para
  escucharlo de noche sin dejar tu color.

### Mejoras
- **Los temas se visten de catálogo**: nombres nuevos en inglés estilo
  Pantone (Aural Classic, Abyssal Indigo, Solar Amber, Prism, Neon Prime,
  Lunar Pearl, Sakura, Nightly Sakura, Midnight X) y cada tema con su
  propio icono en el selector, en vez de emojis. La lista del selector
  ahora tiene scroll: hay 9 temas y vienen más.
- **El ▶ de cada playlist reproduce**: antes abría la lista; ahora la
  música empieza al momento, desde la primera canción.
- **Textos más claros en Playlists**: «Importar playlist» y «Exportar
  lista» (adiós a los «.m3u» en pantalla).
- **Se nota lo que tocas**: todos los tiles y tarjetas responden al
  pulsarlos con su efecto visual.
- **Botones de Now Playing con vida**: play/pause, favorito y el botón de
  modo (repetir/aleatorio) cambian de estado con animación.
- **Menús a tono**: hojas, menús y diálogos toman los colores completos
  del tema activo en los 9 temas.

### Correcciones
- **Vista de letra legible**: el encabezado ya no queda pegado a la barra
  de notificaciones y la flecha de volver se ve sobre el fondo oscuro
  (antes salía negra y se perdía).

### Ronda de ajustes (antes de publicar, probada en la build DEBUG)
- **Botón flotante de reconocimiento**: el micrófono se muda de la barra
  superior a un botón flotante abajo a la derecha, alineado a 16dp del
  borde y del mini-reproductor, con un anillo que pulsa suavemente.
  Un toque y abre «Identificar canción».
- **Letras tipo Spotify**: la ventana de letras muestra ahora la portada,
  el título y el artista de la canción, con una barra de progreso
  debajo que avanza mientras suena.
- **Nightly Sakura, rebalanceado**: fondos carbón casi neutros para que
  el rosa sea el protagonista.
- **7 temas claros nuevos** (16 en total): Royal Blue, Wine Red, Orange,
  Plum, Midnight Indigo, Cherry y Forest — crema de fondo y color fuerte
  de acento, salidos de dúos de diseño.
- **Temas a pantalla completa**: el selector es ahora una pantalla con
  tarjetas tipo splash (icono grande sobre el fondo de cada tema), en
  vez del diálogo modal.
- **La app recuerda dónde estabas**: al volver atrás, las listas de
  álbumes, artistas, playlists y la vista de letra conservan su
  posición.
- **Pull-to-refresh clásico**: la biblioteca se actualiza tirando hacia
  abajo, con el indicador circular que gira mientras rescanea y
  desaparece al terminar; desaparece el botón de actualizar de la
  cabecera.

## v1.0.2 — 2026-10-23

### Novedades
- **Aleatorio en Now Playing**: el botón de repetir ahora es un ciclo de
  cuatro estados — sin repetir, repetir todo, repetir uno y aleatorio. El
  aleatorio mezcla la cola y la repite en bucle, para que la música no se
  detenga al final. (La app no tenía botón de aleatorio visible; la mezcla
  existía por dentro pero sin botón.)

### Correcciones
- **YouTube en Android 10, 11 y 12**: la búsqueda (y con ella las descargas)
  cerraba la app en equipos con Android anterior al 13, porque NewPipe usa
  una función del sistema que solo existe desde Android 13. Ahora la app la
  trae empaquetada (desugaring, la misma solución de la app oficial de
  NewPipe) y funciona en todos los Android compatibles.

## v1.0.1 — 2026-10-22

### Mejoras
- **Reordenar arrastrando, por fin fino**: la cola de reproducción y las
  playlists se reordenan desde la agarradera con un movimiento suave y
  preciso, con auto-scroll al llegar a los bordes — la misma librería que
  usan reproductores open-source como InnerTune y OuterTune.
- **Playlists**: cada canción trae su menú de tres puntos también aquí,
  con «Eliminar de la lista» cuando estás dentro de una playlist.

### Correcciones
- **Descargas**: la canción descargada entra en tu playlist por su ID
  exacto; se acabaron las confusiones con temas del mismo nombre.
- **Arrastrar en playlists**: al soltar ya no se montan unas tarjetas
  sobre otras, y la cola ya no se queda pegada al arrastrar.

### Interno
- Proyecto actualizado a Kotlin 2.0.21 y al nuevo compilador de Compose:
  base más moderna para lo que viene.

## v1.0.0 — 2026-10-22

Primera versión pública de AuralPlayer. 🎧

- Biblioteca local completa: Canciones, Artistas, Álbumes, Playlists,
  Descargas y Carpetas, con índice A–Z, búsqueda y reescaneo.
- Descargas de audio de YouTube dentro de la app (quedan en Música/Aural).
- Letras sincronizadas estilo karaoke y reconocimiento de canciones
  tipo Shazam, también desde un azulejo de ajustes rápidos.
- Ecualizador de 5 bandas con presets y normalización de volumen.
- 8 temas de color para toda la app.
- Portadas personalizables (búsqueda en iTunes) y metadatos editables.
- Importar y exportar playlists en formato .m3u.
- Cola de reproducción reordenable, reproducción sin pausas (gapless),
  temporizador de apagado y velocidad de reproducción ajustable.
