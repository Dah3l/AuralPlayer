// Vista de letras sincronizadas (.lrc)
package com.aural.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aural.player.data.LrcLine
import com.aural.player.data.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LyricsScreen(
    lrcPath: String,
    currentPositionMs: () -> Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lines by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

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
        // Header (con margen de la barra de estado para que no quede pegado)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                "Letra",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
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
            }
        }
    }
}
