// Barra de desplazamiento de escritorio (Android): sin barra, el scroll es táctil.
package com.aural.player.shared

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun LazyListScrollbar(state: LazyListState, modifier: Modifier) {
    // Sin barra en Android.
}

@Composable
actual fun LazyGridScrollbar(state: LazyGridState, modifier: Modifier) {
    // Sin barra en Android.
}
