// Barra lateral A-Z para salto rápido alfabético (puerto de la de Android:
// ocupa el 78% de la altura, pegada a la derecha; muestra un indicador
// grande con la letra actual mientras se arrastra).
// [keys] son las claves de ordenamiento (una por elemento de la lista).
// [onJumpToIndex] recibe el índice del primer elemento con la letra elegida.
package com.aural.player.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AlphabetFastScroller(
    keys: List<String>,
    onJumpToIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val letterMap = remember(keys) {
        val map = mutableMapOf<Char, Int>()
        keys.forEachIndexed { index, key ->
            val first = key.trim().firstOrNull()?.uppercaseChar() ?: return@forEachIndexed
            val letter = if (first in 'A'..'Z') first else '#'
            if (letter !in map) map[letter] = index
        }
        map
    }
    if (letterMap.isEmpty()) return

    var barHeightPx by remember { mutableStateOf(0) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    // Símbolos primero (#), luego A-Z — 27 elementos en total
    val letters = listOf('#') + ('A'..'Z').toList()

    fun letterAtY(y: Float): Char? {
        if (barHeightPx <= 0) return null
        val index = ((y / barHeightPx) * letters.size).toInt().coerceIn(0, letters.size - 1)
        return letters[index]
    }

    fun jumpTo(letter: Char) {
        val idx = letterMap[letter] ?: return
        activeLetter = letter
        onJumpToIndex(idx)
    }

    Box(modifier = modifier) {
        // ---- Barra de letras (derecha, 78% altura) ----
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(0.78f)
                .onGloballyPositioned { barHeightPx = it.size.height }
                .pointerInput(letterMap) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            letterAtY(offset.y)?.let { jumpTo(it) }
                        },
                        onDragEnd = {
                            isDragging = false
                            activeLetter = null
                        },
                        onDragCancel = {
                            isDragging = false
                            activeLetter = null
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            letterAtY(change.position.y)?.let { jumpTo(it) }
                        }
                    )
                }
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    RoundedCornerShape(16.dp)
                )
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            letters.forEach { letter ->
                val enabled = letter in letterMap
                val isActive = letter == activeLetter
                Text(
                    text = if (letter == '#') "•" else letter.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isActive -> MaterialTheme.colorScheme.primary
                        enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    },
                    modifier = Modifier
                        .clickable(enabled = enabled) { jumpTo(letter) }
                        .padding(horizontal = 8.dp)
                )
            }
        }

        // ---- Indicador grande de la letra actual ----
        if (isDragging && activeLetter != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(110.dp)
                    .background(
                        Color.Black.copy(alpha = 0.75f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (activeLetter == '#') "•" else activeLetter.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White
                )
            }
        }
    }
}
