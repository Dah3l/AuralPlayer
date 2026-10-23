# Historial de cambios — AuralPlayer

Todas las novedades de cada versión del APK, de la más reciente a la primera.
Este archivo es la constancia oficial de cambios; las notas de cada release
en GitHub salen de aquí.

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
