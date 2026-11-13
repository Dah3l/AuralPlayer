// Tarjetas para compartir: "Escuchando ahora" y "Tu semana en Aural".
// Mismo patrón de captura que la tarjeta de letras (LyricCard): la vista
// se monta invisible sobre la actividad a densidad fija (320 dpi, 1440 px
// de ancho) y se dibuja a un bitmap que se comparte por el sistema.
package com.aural.player.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.Coil
import coil.request.ImageRequest
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private val CardBg = Color(0xFF121212)
private val CardSurface = Color(0xFF1C1C1C)
private val CardTeal = Color(0xFF2ED3B7)
private val CardMuted = Color(0xFF9A9A9A)

/** "3 h 42 min", "58 min" o "menos de 1 min". */
fun formatListeningTime(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    return when {
        minutes < 1 -> "menos de 1 min"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

/** Carga la portada como bitmap (sin hardware, para dibujarla en la tarjeta). */
suspend fun loadCardCoverBitmap(context: Context, coverUri: String?): Bitmap? {
    if (coverUri.isNullOrBlank()) return null
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

/**
 * Renderiza cualquier contenido Compose a un PNG de 1440 px de ancho y
 * alto ajustado al contenido (máximo 1920). Debe llamarse desde el hilo
 * principal (la vista se monta sobre la actividad).
 */
suspend fun captureCard(
    context: Context,
    content: @Composable () -> Unit
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
    // OJO: el contenido va INVOCADO dentro del lambda de setContent, no
    // pasado como valor: así lo hace la tarjeta de letras (que funciona)
    // y así el compilador de Compose lo compone de verdad.
    composeView.setContent { content() }
    val decor = activity.window.decorView as ViewGroup
    composeView.visibility = android.view.View.INVISIBLE
    decor.addView(composeView, width, ViewGroup.LayoutParams.WRAP_CONTENT)
    composeView.post {
        try {
            val widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(
                width, android.view.View.MeasureSpec.EXACTLY
            )
            val heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(
                0, android.view.View.MeasureSpec.UNSPECIFIED
            )
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
suspend fun shareCardImage(
    context: Context,
    bitmap: Bitmap,
    fileName: String,
    chooserTitle: String,
    extraText: String? = null
) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share_cards").apply { mkdirs() }
        val out = File(dir, fileName)
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
        if (extraText != null) putExtra(Intent.EXTRA_TEXT, extraText)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

@Composable
fun NowPlayingCardContent(
    title: String,
    artist: String,
    coverBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CardBg)
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.material3.Text(
            text = "ESCUCHANDO AHORA",
            color = CardTeal,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )
        Spacer(Modifier.height(32.dp))
        if (coverBitmap != null) {
            Image(
                bitmap = coverBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(440.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(440.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardSurface),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text(
                    text = "♪",
                    color = Color.White,
                    fontSize = 160.sp
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        androidx.compose.material3.Text(
            text = title,
            color = Color.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.Text(
            text = artist,
            color = CardMuted,
            fontSize = 28.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(40.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Text(
                text = "Aural",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            androidx.compose.material3.Text(
                text = "Player",
                color = CardTeal,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(12.dp))
            androidx.compose.material3.Text(
                text = "· El sonido, sin ruido.",
                color = CardMuted,
                fontSize = 24.sp
            )
        }
    }
}

@Composable
fun WeekCardContent(
    totalSeconds: Long,
    distinctSongs: Int,
    topSongs: List<com.aural.player.data.WeekSongStat>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CardBg)
            .padding(48.dp)
    ) {
        androidx.compose.material3.Text(
            text = "TU SEMANA EN AURAL",
            color = CardTeal,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )
        Spacer(Modifier.height(24.dp))
        androidx.compose.material3.Text(
            text = formatListeningTime(totalSeconds),
            color = Color.White,
            fontSize = 64.sp,
            fontWeight = FontWeight.ExtraBold
        )
        androidx.compose.material3.Text(
            text = "de música esta semana · $distinctSongs canciones distintas",
            color = CardMuted,
            fontSize = 24.sp
        )
        Spacer(Modifier.height(36.dp))
        topSongs.take(3).forEachIndexed { index, song ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                androidx.compose.material3.Text(
                    text = "${index + 1}",
                    color = CardTeal,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(48.dp)
                )
                Column(Modifier.weight(1f)) {
                    androidx.compose.material3.Text(
                        text = song.title,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    androidx.compose.material3.Text(
                        text = song.artist,
                        color = CardMuted,
                        fontSize = 22.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(16.dp))
                androidx.compose.material3.Text(
                    text = formatListeningTime(song.seconds),
                    color = Color.White,
                    fontSize = 24.sp
                )
            }
        }
        Spacer(Modifier.height(36.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Text(
                text = "Aural",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            androidx.compose.material3.Text(
                text = "Player",
                color = CardTeal,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(12.dp))
            androidx.compose.material3.Text(
                text = "· El sonido, sin ruido.",
                color = CardMuted,
                fontSize = 24.sp
            )
        }
    }
}
