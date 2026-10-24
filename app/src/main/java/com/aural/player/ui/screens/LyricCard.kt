// Tarjetas de letras para compartir (estilo Spotify lyric cards)
package com.aural.player.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.core.content.FileProvider
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.ImageRequest
import com.aural.player.R
import com.aural.player.ui.theme.AppTheme
import com.aural.player.ui.theme.colorScheme
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlin.math.ceil
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Fondo de la tarjeta: automático (color de la portada) o un tema. */
sealed interface CardBackground {
    data object Auto : CardBackground
    data class Theme(val theme: AppTheme) : CardBackground
}

/** Máximo de líneas que caben en la tarjeta (como en la referencia). */
private const val MAX_CARD_LINES = 6

/** Texto negro o blanco según lo claro que sea el fondo. */
private fun textOn(bg: Color): Color {
    val lum = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
    return if (lum > 0.6f) Color.Black else Color.White
}

/** Aclara un color mezclándolo con blanco (tarjeta interior). */
private fun lighten(c: Color, fraction: Float): Color = Color(
    red = c.red + (1f - c.red) * fraction,
    green = c.green + (1f - c.green) * fraction,
    blue = c.blue + (1f - c.blue) * fraction,
    alpha = 1f
)

/**
 * Elige la fuente de los versos para el ancho de exportación (592 dp
 * útiles): la mayor que hace que cada verso ocupe como mucho 2 líneas
 * visuales y que el alto total no pase del máximo 3:4 (800 dp de
 * contenido). Así la tarjeta nunca queda con huecos enormes ni corta
 * un verso.
 */
fun selectCardFontSize(lines: List<String>): TextUnit {
    val contentW = 592f
    val maxContentH = 800f
    for (size in listOf(40f, 36f, 32f, 28f, 24f, 22f, 20f, 18f, 16f)) {
        val visual = lines.sumOf { line ->
            minOf(
                3,
                ceil(line.length * 0.58f * size / contentW)
                    .toInt().coerceAtLeast(1)
            )
        }
        val totalH = 64f + 24f + visual * size * 1.35f +
            (lines.size - 1).coerceAtLeast(0) * 8f + 48f + 44f
        if (totalH <= maxContentH) return size.sp
    }
    return 16.sp
}

/**
 * La tarjeta en sí: fondo de color, tarjeta interior más clara,
 * portada + título + artista arriba, versos en negrita y marca Aural.
 * El alto se ajusta al contenido (sin huecos): la tarjeta termina
 * justo después de la marca. `scale` permite dibujarla más pequeña
 * en la vista previa con las mismas proporciones exactas.
 */
@Composable
fun LyricCardContent(
    title: String,
    artist: String,
    coverBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    lines: List<String>,
    background: Color,
    lineFontSize: TextUnit,
    scale: Float = 1f,
    modifier: Modifier = Modifier
) {
    val textColor = textOn(background)
    val cardColor = lighten(background, 0.10f)
    Box(
        modifier = modifier
            .background(background)
            .padding(horizontal = 32.dp * scale, vertical = 48.dp * scale)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp * scale))
                .background(cardColor)
                .padding(32.dp * scale)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp * scale)
                            .clip(RoundedCornerShape(14.dp * scale)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp * scale)
                            .clip(RoundedCornerShape(14.dp * scale))
                            .background(textColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(44.dp * scale),
                            colorFilter = ColorFilter.tint(textColor)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp * scale))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 26.sp * scale,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
                        fontSize = 20.sp * scale,
                        color = textColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(24.dp * scale))
            lines.forEachIndexed { index, line ->
                if (index > 0) Spacer(Modifier.height(8.dp * scale))
                Text(
                    text = line,
                    fontSize = lineFontSize * scale,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor,
                    lineHeight = lineFontSize * scale * 1.35f,
                    // Wrap completo; el tope de 3 líneas visuales
                    // solo entra en casos extremos
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(48.dp * scale))
            // Marca con protagonismo, como el logo de Spotify
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp * scale),
                    colorFilter = ColorFilter.tint(textColor)
                )
                Spacer(Modifier.width(10.dp * scale))
                Text(
                    text = "Aural",
                    fontSize = 26.sp * scale,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor
                )
            }
        }
    }
}

/** Carga la portada como bitmap (sin hardware, para poder leerla con Palette). */
private suspend fun loadCoverBitmap(context: Context, coverUri: String?): Bitmap? {
    if (coverUri == null) return null
    return try {
        val request = ImageRequest.Builder(context)
            .data(coverUri)
            .allowHardware(false)
            .build()
        val drawable = Coil.imageLoader(context).execute(request).drawable
        (drawable as? BitmapDrawable)?.bitmap
    } catch (e: Exception) {
        null
    }
}

/** Color dominante de la portada (null si no hay portada o falla la carga). */
private suspend fun dominantColorOf(bitmap: Bitmap?): Color? {
    if (bitmap == null) return null
    return try {
        val swatch = Palette.from(bitmap).generate().dominantSwatch ?: return null
        Color(swatch.rgb)
    } catch (e: Exception) {
        null
    }
}

/**
 * Renderiza la tarjeta a un PNG de 1440 px de ancho y alto ajustado
 * al contenido (máximo 1920, proporción 3:4). La vista se monta
 * invisible sobre la actividad para que la composición corra y luego
 * se dibuja. Se usa una densidad fija (320 dpi) para que el diseño
 * sea idéntico en cualquier teléfono: 1440 px = 720 dp siempre.
 */
private suspend fun captureLyricCard(
    context: Context,
    title: String,
    artist: String,
    coverBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    lines: List<String>,
    background: Color,
    lineFontSize: TextUnit
): Bitmap = suspendCancellableCoroutine { cont ->
    val activity = context as? Activity
    if (activity == null) {
        cont.resume(Bitmap.createBitmap(1440, 1920, Bitmap.Config.ARGB_8888))
        return@suspendCancellableCoroutine
    }
    val width = 1440
    val fixedConfig = android.content.res.Configuration(context.resources.configuration).apply {
        densityDpi = 320
        fontScale = 1f
    }
    val fixedContext = context.createConfigurationContext(fixedConfig)
    val composeView = ComposeView(fixedContext)
    composeView.setContent {
        LyricCardContent(
            title = title,
            artist = artist,
            coverBitmap = coverBitmap,
            lines = lines,
            background = background,
            lineFontSize = lineFontSize,
            modifier = Modifier.fillMaxWidth()
        )
    }
    val decor = activity.window.decorView as ViewGroup
    composeView.visibility = View.INVISIBLE
    decor.addView(composeView, width, ViewGroup.LayoutParams.WRAP_CONTENT)
    composeView.post {
        try {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            composeView.measure(widthSpec, heightSpec)
            val height = composeView.measuredHeight.coerceIn(1, 1920)
            composeView.layout(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            composeView.draw(android.graphics.Canvas(bitmap))
            cont.resume(bitmap)
        } catch (e: Exception) {
            cont.resume(Bitmap.createBitmap(width, 1920, Bitmap.Config.ARGB_8888))
        } finally {
            decor.removeView(composeView)
        }
    }
}

/** Guarda el PNG en caché y lo comparte con la hoja del sistema. */
private suspend fun shareBitmap(context: Context, bitmap: Bitmap) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "lyric_cards").apply { mkdirs() }
        val out = File(dir, "aural-lyric.png")
        FileOutputStream(out).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        out
    }
    val uri = FileProvider.getUriForFile(
        context, "${context.packageName}.fileprovider", file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir letra"))
}

/**
 * Hoja de la tarjeta: paso 1 elegir líneas (máx 6), paso 2 vista previa
 * con picker de fondo (Auto = portada, o cualquier tema) y compartir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricCardSheet(
    title: String,
    artist: String,
    coverUri: String?,
    lines: List<String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var autoBg by remember { mutableStateOf<Color?>(null) }
    var bgChoice by remember { mutableStateOf<CardBackground>(CardBackground.Auto) }
    var sharing by remember { mutableStateOf(false) }

    // Cargar portada y color dominante una sola vez
    LaunchedEffect(coverUri) {
        val bmp = loadCoverBitmap(context, coverUri)
        coverBitmap = bmp
        autoBg = dominantColorOf(bmp)
    }

    val themePrimary = MaterialTheme.colorScheme.primary
    val background = when (val choice = bgChoice) {
        is CardBackground.Auto -> autoBg ?: themePrimary
        is CardBackground.Theme -> choice.theme.colorScheme().primary
    }
    val selectedLines = selected.sorted().map { lines[it] }
    val cardFont = selectCardFontSize(selectedLines)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = if (step == 0) "Elige las líneas" else "Tu tarjeta",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            if (step == 0) {
                Text(
                    text = "${selected.size}/$MAX_CARD_LINES líneas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(lines) { index, line ->
                        val isSelected = index in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .clickable {
                                    selected = if (isSelected) {
                                        selected - index
                                    } else if (selected.size < MAX_CARD_LINES) {
                                        selected + index
                                    } else {
                                        selected
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                IconCheck()
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { step = 1 },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Vista previa")
                }
                Spacer(Modifier.height(16.dp))
            } else {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Vista previa: la misma tarjeta, dibujada de
                    // forma nativa con todas las medidas a escala del
                    // ancho disponible, para que lo que ves sea lo
                    // que se exporta (sin trucos de capa)
                    BoxWithConstraints(
                        Modifier.fillMaxWidth()
                    ) {
                        val scale = maxWidth.value / 720f
                        LyricCardContent(
                            title = title,
                            artist = artist,
                            coverBitmap = coverBitmap?.asImageBitmap(),
                            lines = selectedLines,
                            background = background,
                            lineFontSize = cardFont,
                            scale = scale,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Fondo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Auto: color dominante de la portada
                        item {
                            ThemeSwatch(
                                color = autoBg ?: themePrimary,
                                label = "Auto",
                                selected = bgChoice is CardBackground.Auto,
                                onClick = { bgChoice = CardBackground.Auto }
                            )
                        }
                        itemsIndexed(AppTheme.entries) { _, theme ->
                            ThemeSwatch(
                                color = theme.colorScheme().primary,
                                label = theme.displayName,
                                selected = bgChoice == CardBackground.Theme(theme),
                                onClick = { bgChoice = CardBackground.Theme(theme) }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { step = 0 },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cambiar líneas")
                    }
                    Button(
                        onClick = {
                            if (sharing) return@Button
                            sharing = true
                            scope.launch {
                                try {
                                    val bitmap = captureLyricCard(
                                        context = context,
                                        title = title,
                                        artist = artist,
                                        coverBitmap = coverBitmap?.asImageBitmap(),
                                        lines = selectedLines,
                                        background = background,
                                        lineFontSize = cardFont
                                    )
                                    shareBitmap(context, bitmap)
                                } catch (e: Exception) {
                                } finally {
                                    sharing = false
                                }
                            }
                        },
                        enabled = !sharing,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (sharing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Compartir")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Círculo de color del picker de fondo (con ✓ si está activo). */
@Composable
private fun ThemeSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (selected) Modifier.border(
                        2.5.dp, MaterialTheme.colorScheme.primary, CircleShape
                    ) else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                IconCheckOn(color)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IconCheck() {
    androidx.compose.material3.Icon(
        Icons.Rounded.Check,
        contentDescription = "Seleccionada",
        tint = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun IconCheckOn(bg: Color) {
    androidx.compose.material3.Icon(
        Icons.Rounded.Check,
        contentDescription = "Fondo activo",
        tint = textOn(bg),
        modifier = Modifier.size(20.dp)
    )
}
