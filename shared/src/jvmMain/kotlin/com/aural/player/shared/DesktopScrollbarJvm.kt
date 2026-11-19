// Barra de desplazamiento de escritorio (JVM): la barra real de Compose.
package com.aural.player.shared

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun LazyListScrollbar(state: LazyListState, modifier: Modifier) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier
    )
}

@Composable
actual fun LazyGridScrollbar(state: LazyGridState, modifier: Modifier) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier
    )
}
