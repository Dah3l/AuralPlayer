// Vista de letras sincronizadas (.lrc)
package com.aural.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.aural.player.data.LrcLine
import com.aural.player.data.LrcParser
import com.aural.player.data.LyricsTranslator
import com.aural.player.data.MeaningClient
import com.aural.player.AuralLink
import com.aural.player.ui.components.CoverArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LyricsScreen(
    lrcPath: String,
    title: String,
    artist: String,
    coverUri: String?,
    currentPositionMs: () -> Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // Estado guardable: al cerrar y reabrir la letra, la lista vuelve a
    // la posición donde la dejó el usuario.
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    // Si había una posición restaurada, el primer auto-scroll no debe
    // pisarla (los siguientes sí siguen a la canción como siempre)
    var skipFirstAutoScroll by remember {
        mutableStateOf(listState.firstVisibleItemIndex > 0)
    }
    var showCardSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- Significado (se busca solo si el usuario baja hasta el final) ---
    var meaningRequested by remember(title, artist) { mutableStateOf(false) }
    var meaningText by remember(title, artist) { mutableStateOf<String?>(null) }
    var meaningDone by remember(title, artist) { mutableStateOf(false) }

    // --- Traducción al español (ML Kit, en el teléfono) ---
    var detectedLang by remember(title, artist) { mutableStateOf<String?>(null) }
    var langDetectionDone by remember(title, artist) { mutableStateOf(false) }
    var translatedLines by remember(title, artist) { mutableStateOf<List<String>?>(null) }
    var showTranslation by remember(title, artist) { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var translateStatus by remember { mutableStateOf<String?>(null) }
    var translateError by remember(title, artist) { mutableStateOf<String?>(null) }

    // Zona del desvanecido superior de las líneas (96dp): al subir, la
    // línea se funde en el mesh en vez de taparse con una sombra.
    val fadePx = with(LocalDensity.current) { 96.dp.toPx() }

    // Cargar y parsear el .lrc
    LaunchedEffect(lrcPath) {
        loading = true
        lines = withContext(Dispatchers.IO) {
            try {
                val content = File(lrcPath).readText()
                LrcParser.parse(content)
            } catch (e: Exception) { emptyList() }
        }
        loading = false
    }

    // Detectar el idioma de la letra (modelo en el teléfono, sin internet)
    LaunchedEffect(lines) {
        if (lines.isEmpty() || langDetectionDone) return@LaunchedEffect
        detectedLang = LyricsTranslator.detectLanguage(lines.joinToString("\n") { it.text })
        langDetectionDone = true
    }

    // Flujo de traducción: caché → modelos (con diálogo) → traducir → mostrar
    fun startTranslation() {
        val lang = detectedLang
        translateError = null
        if (lang == null) {
            translateError = "No se pudo detectar el idioma de esta letra."
            return
        }
        if (lang == LyricsTranslator.TARGET_TAG || !LyricsTranslator.isSupported(lang)) {
            translateError = "Este idioma no se puede traducir al español."
            return
        }
        val originals = lines.map { it.text }
        scope.launch {
            val cached = LyricsTranslator.getCached(context, lang, originals)
            if (cached != null) {
                translatedLines = cached
                showTranslation = true
                return@launch
            }
            if (!LyricsTranslator.areModelsReady(lang)) {
                showDownloadDialog = true
                return@launch
            }
            translateStatus = "Traduciendo…"
            val result = LyricsTranslator.translateLines(originals, lang) { done, total ->
                translateStatus = "Traduciendo… $done de $total"
            }
            translateStatus = null
            if (result != null) {
                LyricsTranslator.putCached(context, lang, originals, result)
                translatedLines = result
                showTranslation = true
            } else {
                translateError = "No se pudo traducir. Revisa tu conexión e inténtalo de nuevo."
            }
        }
    }

    fun downloadAndTranslate() {
        val lang = detectedLang ?: return
        val originals = lines.map { it.text }
        showDownloadDialog = false
        scope.launch {
            translateStatus = "Descargando los modelos…"
            val ok = LyricsTranslator.downloadModels(lang)
            if (!ok) {
                translateStatus = null
                translateError = "No se pudieron descargar los modelos. Revisa tu conexión e inténtalo de nuevo."
                return@launch
            }
            translateStatus = "Traduciendo…"
            val result = LyricsTranslator.translateLines(originals, lang) { done, total ->
                translateStatus = "Traduciendo… $done de $total"
            }
            translateStatus = null
            if (result != null) {
                LyricsTranslator.putCached(context, lang, originals, result)
                translatedLines = result
                showTranslation = true
            } else {
                translateError = "No se pudo traducir. Inténtalo de nuevo."
            }
        }
    }

    // Actualizar la línea activa según la posición (tiempo real)
    // Usamos un ticker que fuerza recomposición cada 500ms
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            tick++
        }
    }
    val activeIndex = remember(lines, tick) {
        if (lines.isNotEmpty()) LrcParser.currentIndex(lines, currentPositionMs())
        else 0
    }
    // Líneas que se muestran: traducidas si el usuario activó la traducción
    val displayLines = remember(lines, translatedLines, showTranslation) {
        if (showTranslation && translatedLines != null && translatedLines!!.size == lines.size) {
            lines.mapIndexed { i, line -> line.copy(text = translatedLines!![i]) }
        } else lines
    }
    // Auto-scroll cuando cambia la línea activa
    LaunchedEffect(activeIndex) {
        if (skipFirstAutoScroll) {
            skipFirstAutoScroll = false
            return@LaunchedEffect
        }
        if (lines.isNotEmpty()) {
            try {
                listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
            } catch (e: Exception) { }
        }
    }

    // Sin fondo propio: el mesh vive detrás (en el overlay del Now
    // Playing) y el velo de la ventana da el contraste para leer.
    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Cabecera tipo Spotify (con margen de la barra de estado para
        // que no quede pegada): portada + título + artista de la canción
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            CoverArt(uri = coverUri, size = 64.dp, cornerRadius = 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (langDetectionDone && detectedLang != null && detectedLang != LyricsTranslator.TARGET_TAG) {
                IconButton(onClick = {
                    if (translatedLines != null) showTranslation = !showTranslation
                    else startTranslation()
                }) {
                    Icon(
                        Icons.Filled.Translate,
                        contentDescription = if (showTranslation) "Ver letra original" else "Ver en español",
                        tint = if (showTranslation) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            if (lines.isNotEmpty()) {
                IconButton(onClick = { showCardSheet = true }) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Compartir tarjeta de letra",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }


        if (translateError != null) {
            Text(
                translateError!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
        }

        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            lines.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No se pudo leer la letra",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = 24.dp,
                            vertical = 32.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(displayLines) { index, line ->
                            val isActive = index == activeIndex
                            // Desvanecido superior: la línea se funde en
                            // el mesh al subir (su alpha baja según su
                            // borde inferior se acerca al borde de arriba
                            // de la lista), sin banda de sombra.
                            val itemInfo = listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.index == index }
                            val itemAlpha = if (itemInfo == null) 1f
                            else ((itemInfo.offset + itemInfo.size) / fadePx)
                                .coerceIn(0f, 1f)
                            val windowEnd = displayLines.getOrNull(index + 1)?.timeMs
                                ?: (line.timeMs + 4000L)
                            val karaokeWindow = windowEnd - line.timeMs >= 600L
                            if (isActive && karaokeWindow) {
                                KaraokeLine(
                                    text = line.text,
                                    startMs = line.timeMs,
                                    endMs = windowEnd,
                                    positionMs = currentPositionMs,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .alpha(itemAlpha)
                                )
                            } else {
                                Text(
                                    line.text,
                                    fontSize = if (isActive) 22.sp else 17.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isActive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .alpha(itemAlpha)
                                )
                            }
                        }
                        item {
                            // Significado: se busca al llegar al final de la letra
                            LaunchedEffect(Unit) {
                                if (!meaningRequested) {
                                    meaningRequested = true
                                    meaningText = MeaningClient.getMeaning(context, artist, title)
                                    meaningDone = true
                                }
                            }
                            when {
                                !meaningDone -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "Buscando el significado…",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                meaningText != null -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "Significado",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            meaningText!!,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "Fuente: letras.com (generado automáticamente)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                else -> {
                                    Text(
                                        "No se encontró el significado de esta canción.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Hoja para crear la tarjeta de letra con las líneas elegidas
    if (showCardSheet) {
        LyricCardSheet(
            title = title,
            artist = artist,
            coverUri = coverUri,
            lines = lines.map { it.text }.filter { it.isNotBlank() },
            onDismiss = { showCardSheet = false },
            linkText = AuralLink.shareText(title, artist)
        )
    }

    // Diálogo de confirmación antes de bajar los modelos de traducción
    if (showDownloadDialog) {
        val langName = detectedLang?.let { LyricsTranslator.displayName(it) } ?: ""
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("Descargar traducción") },
            text = {
                Text(
                    "Para traducir esta letra hay que descargar los modelos de $langName y español " +
                    "(unos ${LyricsTranslator.MODEL_SIZE_MB_EACH} MB cada uno). Es una descarga única: " +
                    "después la traducción funciona sin internet. Se recomienda Wi-Fi."
                )
            },
            confirmButton = {
                TextButton(onClick = { downloadAndTranslate() }) { Text("Descargar") }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) { Text("Ahora no") }
            }
        )
    }

    // Progreso de descarga / traducción
    if (translateStatus != null) {
        AlertDialog(
            onDismissRequest = { },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(translateStatus!!)
                }
            },
            confirmButton = { }
        )
    }
}

/**
 * Línea de karaoke: las palabras se van iluminando según avanza la canción.
 * Los tiempos de cada palabra se interpolan según su largo dentro de la
 * ventana de la línea (las fuentes gratis no traen tiempos por palabra).
 */
@Composable
private fun KaraokeLine(
    text: String,
    startMs: Long,
    endMs: Long,
    positionMs: () -> Long,
    modifier: Modifier = Modifier
) {
    var now by remember(text, startMs) { mutableStateOf(positionMs()) }
    LaunchedEffect(text, startMs) {
        while (true) {
            kotlinx.coroutines.delay(100)
            now = positionMs()
        }
    }
    val windowMs = (endMs - startMs).coerceIn(600L, 10_000L)
    val words = remember(text) { text.split(" ").filter { it.isNotEmpty() } }
    // Límites de cada palabra (proporcional a su largo + 1 por el espacio)
    val boundaries = remember(words, windowMs) {
        val weights = words.map { it.length + 1 }
        val total = weights.sum().coerceAtLeast(1)
        var acc = 0L
        weights.map { w ->
            acc += windowMs * w / total
            startMs + acc
        }
    }
    val annotated = buildAnnotatedString {
        var first = true
        words.forEachIndexed { i, word ->
            if (!first) append(" ")
            first = false
            val sung = now >= boundaries[i]
            withStyle(
                SpanStyle(
                    color = if (sung) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) { append(word) }
        }
    }
    Text(
        annotated,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}
