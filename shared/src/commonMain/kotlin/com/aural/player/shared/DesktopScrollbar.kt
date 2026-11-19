// Barra de desplazamiento vertical de escritorio para las listas
// principales de la biblioteca. En Android no hace nada (el scroll
// es táctil); en PC dibuja la barra con su thumb completo, sin que
// la tape la barra A-Z (que deja 14 dp libres a la derecha).
package com.aural.player.shared

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun LazyListScrollbar(state: LazyListState, modifier: Modifier = Modifier)

@Composable
expect fun LazyGridScrollbar(state: LazyGridState, modifier: Modifier = Modifier)
