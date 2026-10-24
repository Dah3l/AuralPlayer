// Vista de letras sincronizadas (.lrc)
package com.aural.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.Brush
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
import com.aural.player.ui.components.CoverArt
import kotlinx.coroutines.Dispatchers
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                        itemsIndexed(lines) { index, line ->
                            val isActive = index == activeIndex
                            Text(
                                line.text,
                                fontSize = if (isActive) 22.sp else 17.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    // Sombra-difuminado: las líneas se funden con el fondo
                    // al pasar por debajo del header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.background,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0f)
                                    )
                                )
                            )
                    )
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
            onDismiss = { showCardSheet = false }
        )
    }
}
