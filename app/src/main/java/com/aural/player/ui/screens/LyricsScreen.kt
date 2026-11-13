// Vista de letras sincronizadas (.lrc)
package com.aural.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.aural.player.data.LrcLine
import com.aural.player.data.LrcParser
import com.aural.player.data.LyricsTranslator
// APARCADO 2026-11-13: import com.aural.player.data.MeaningClient
import com.aural.player.AuralLink
import com.aural.player.ui.components.CoverArt
import com.aural.player.ui.components.FeatureDot
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    showTranslateDot: Boolean = false,
    onTranslateInteracted: () -> Unit = {},
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

    /* SIGNIFICADO APARCADO 2026-11-13 — para reactivar, quitar este comentario,
       el del item de la lista y el del import de MeaningClient.
    // --- Significado (se busca solo si el usuario baja hasta el final) ---
    var meaningRequested by remember(title, artist) { mutableStateOf(false) }
    var meaningText by remember(title, artist) { mutableStateOf<String?>(null) }
    var meaningDone by remember(title, artist) { mutableStateOf(false) }
    */

    // --- Traducción al español (ML Kit, en el teléfono) ---
    var detectedLang by remember(title, artist) { mutableStateOf<String?>(null) }
    var langDetectionDone by remember(title, artist) { mutableStateOf(false) }
    var translatedLines by remember(title, artist) { mutableStateOf<List<String>?>(null) }
    var showTranslation by remember(title, artist) { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var translateStatus by remember(title, artist) { mutableStateOf<String?>(null) }
    var translateError by remember(title, artist) { mutableStateOf<String?>(null) }
    // El usuario pidió traducir ESTA canción y se espera a los modelos:
    // al terminar la descarga, la traducción arranca sola.
    var translatePending by remember(title, artist) { mutableStateOf(false) }
    // Estado global de las descargas (sobrevive al cambio de canción).
    val modelStates by LyricsTranslator.modelStates.collectAsStateWithLifecycle()
    val modelState = detectedLang?.let { modelStates[it] }

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

    // Flujo de traducción: caché → modelos (con diálogo) → traducir → mostrar.
    // La descarga vive en LyricsTranslator (segundo plano): si el usuario
    // cambia de canción, la descarga sigue y al terminar traduce sola
    // la canción que la pidió (si sigue en pantalla).
    fun runTranslation(lang: String, originals: List<String>) {
        scope.launch {
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

    // La descarga terminó: traducir la canción que la pidió, o avisar el fallo.
    LaunchedEffect(modelState, detectedLang, translatePending) {
        if (!translatePending) return@LaunchedEffect
        val lang = detectedLang ?: return@LaunchedEffect
        when (modelState) {
            LyricsTranslator.ModelState.READY -> {
                if (translatedLines == null) {
                    translatePending = false
                    runTranslation(lang, lines.map { it.text })
                }
            }
            LyricsTranslator.ModelState.FAILED -> {
                translatePending = false
                translateError =
                    "No se pudieron descargar los modelos. Revisa tu conexión e inténtalo de nuevo."
            }
            else -> {}
        }
    }

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
            when (LyricsTranslator.modelStates.value[lang]) {
                // Ya está bajando (quizás desde otra canción): esperar a que termine.
                LyricsTranslator.ModelState.DOWNLOADING -> {
                    translatePending = true
                    return@launch
                }
                LyricsTranslator.ModelState.READY -> {
                    runTranslation(lang, originals)
                    return@launch
                }
                else -> {
                    if (!LyricsTranslator.areModelsReady(lang)) {
                        showDownloadDialog = true
                        return@launch
                    }
                    LyricsTranslator.refreshState(lang)
                    runTranslation(lang, originals)
                }
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
                Box {
                    IconButton(onClick = {
                        onTranslateInteracted()
                        if (translatedLines != null) showTranslation = !showTranslation
                        else startTranslation()
                    }) {
                        if (modelState == LyricsTranslator.ModelState.DOWNLOADING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Filled.Translate,
                                contentDescription = if (showTranslation) "Ver letra original" else "Ver en español",
                                tint = if (showTranslation) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                    if (showTranslateDot) {
                        FeatureDot(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 8.dp)
                        )
                    }
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


        // Descarga de modelos en segundo plano: no bloquea la app y
        // sigue aunque cambies de canción.
        if (modelState == LyricsTranslator.ModelState.DOWNLOADING) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Descargando los modelos de traducción… Puedes seguir usando la app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Traduciendo (rápido, en el teléfono): texto en línea, sin diálogo.
        if (translateStatus != null) {
            Text(
                translateStatus!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
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
/* SIGNIFICADO APARCADO 2026-11-13 — item de la lista. Para reactivar, quitar este comentario. */
/*
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
                        */
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
                    "(unos ${LyricsTranslator.MODEL_SIZE_MB_TOTAL} MB entre los dos). Es una descarga única: " +
                    "después la traducción funciona sin internet. Se recomienda Wi-Fi. Si la descarga no avanza, prueba con VPN."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDownloadDialog = false
                    // La descarga arranca en segundo plano y la traducción
                    // de esta canción se dispara sola al terminar.
                    detectedLang?.let { LyricsTranslator.startDownload(it) }
                    translatePending = true
                }) { Text("Descargar") }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) { Text("Ahora no") }
            }
        )
    }
}
