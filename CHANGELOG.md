# Historial de cambios — AuralPlayer

Todas las novedades de cada versión del APK, de la más reciente a la primera.
Este archivo es la constancia oficial de cambios; las notas de cada release
en GitHub salen de aquí.

## Sin publicar (próxima versión)

### Novedades
- **Aural Cards: guardar en la galería** — además de compartir, la tarjeta ahora se puede guardar directo en la galería (carpeta Aural) con el botón "Guardar en galería".
- **Home remodelado** — saludo según la hora en español y sin emojis, Aural grande con el logo al lado y el saludo debajo, búsqueda siempre visible, filtros en pills y header fijo (nada se encoge al scrollear); el menú ⋮ queda en la esquina superior derecha, como antes. Degradados muy sutiles en la búsqueda, las pills y el mini reproductor para que el home no se vea tan plano; el botón del micro queda 8dp más a la izquierda para no tocar la barra de desplazamiento.
- **Más reproducidas** — nueva sección en el home con las 2 playlists que más suenan, en tarjetas estilo For You con botón "Ver todas" a la pantalla de playlists; sin datos todavía muestra Favoritas y Descargas. Título y botón ▶ van dentro de la portada, sobre un degradado oscuro, como en la referencia.

### Mejoras
- **Detalle con imagen grande** — playlists, álbumes y artistas ahora abren con su portada en grande a lo ancho, con el título encima, y al subir la imagen se recoge con animación dejando solo la barra con el título y la flecha de volver.
- **Recorte de portada 1:1** — al elegir una foto para una playlist, ahora se muestra un editor con cuadrícula para ajustar el recorte cuadrado (arrastrar y pellizcar) antes de guardarla.
- **Título del detalle con más peso** — y se desvanece cuando el scroll pasa el 75% de la imagen, para que no choque con el título de la barra al colapsar.
- **Menú ⋮ para la foto de la playlist** — arriba a la derecha: agregar imagen o eliminar la actual (la foto ya no se cambia tocando la imagen). Sin foto, el detalle vuelve a su imagen por defecto.
- **Barra colapsada más alta y a tono** — un poco más alta, con la tonalidad del Now Playing, y la franja del notch con su mismo color.
- **Barra colapsada ya no deja pasar los toques** — antes un toque en la barra caía en la lista de detrás (podía sonar una canción sin querer); ahora la barra consume sus propios toques.

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
- **Aviso de nueva versión**: al abrir la app (máximo una vez al día)
  Aural consulta el último release en GitHub y, si hay uno más nuevo
  que la versión instalada, muestra un diálogo con "Descargar" (abre
  la página del release en el navegador) y "Más tarde" (no vuelve a
  avisar por esa versión). En el menú ⋮ hay "Buscar actualizaciones"
  para comprobar a mano, con aviso de "Ya tienes la última versión"
  o de error si no hay conexión. Sin backend ni permisos nuevos; si
  GitHub no responde, el chequeo automático no muestra nada. La
  versión de "Acerca de" ahora se lee del build, no está escrita a
  mano.
- **Tarjetas de letras para compartir**: desde la vista de letras, el
  botón de compartir abre el selector de líneas (máx 6) y genera una
  tarjeta de 1440 px de ancho y alto ajustado al contenido (máximo
  3:4) con portada, título, artista, las líneas elegidas con su salto
  completo y la marca Aural con protagonismo (logo grande + nombre +
  eslogan "El sonido, sin ruido."); el fondo es un degradado de dos
  tonos sacados de la portada (o del tema elegido en el picker) y la
  onda de Aural aparece como marca de agua detrás de los versos. La
  fuente de los versos se elige según el contenido (hasta 40sp) para
  que nunca se corte un verso ni quede hueco, y la vista previa
  dibuja la tarjeta de forma nativa a escala exacta de lo que se
  exporta. Se comparte como imagen por la hoja del sistema (WhatsApp,
  Instagram, Telegram…).
- **Fix**: la foto de playlist elegida de la galería ahora se muestra
  también dentro de la playlist, no solo en la lista (antes el detalle
  seguía mostrando la portada de la primera canción).
- **Fix**: la vista de letras ya no deja pasar toques al Now Playing
  de detrás (al pulsar junto al botón de compartir se abría el menú ⋮,
  que está casi en el mismo sitio). El mismo bloqueo se aplicó al
  splash de inicio, que tampoco lo tenía. Revisadas el resto de
  vistas: hojas inferiores y diálogos ya consumen los toques fuera de
  ellos, y el propio Now Playing ya bloqueaba los de la biblioteca.
- **Aural Cards**: más contraste entre el fondo y la tarjeta (la
  tarjeta aclara un 22% y el degradado del fondo va de un 80% a un
  45% del color base, para que no se fundan); el color del texto se
  calcula ahora sobre la tarjeta, no sobre el color base.
- **Cabecera de letras más grande** y difuminado bajo el header: las
  líneas se funden con el fondo al pasar por debajo.
- **Favoritas se comporta como las demás**: su lista ya tiene drag para
  reordenar (el orden se guarda) y su menú ⋯ ofrece «Eliminar de la
  lista» para quitar el favorito.
- **Importar con su nombre**: la playlist importada se llama como el
  archivo elegido (o su título #PLAYLIST), no «Importada 23/10»; si el
  nombre existe, se numera («Nombre 2», «Nombre 3»…).
- **Fotos en tus playlists**: tocar la portada en el detalle de una
  playlist abre el selector de foto; esa foto se ve en la miniatura de
  la lista. Favoritas y Descargas conservan su corazón y su símbolo de
  descarga, sin cambios.
- **La búsqueda no se esconde con texto**: la barra solo se cierra
  cuando el campo está vacío.
- **Temas sin subtítulo**: las tarjetas muestran solo el nombre del tema.
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
