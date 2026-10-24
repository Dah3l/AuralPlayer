// Editor de recorte 1:1 para portadas de playlist: grid de tercios y
// preview editable (arrastrar para mover, pellizcar para acercar).
// Lo que se ve en el cuadro es lo que se guarda, cuadrado 1080x1080.
package com.aural.player.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.onSizeChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** Carga la imagen con muestreo para no agotar memoria (máx 2048 px por lado). */
private fun decodeSampledBitmap(context: Context, uriString: String): Bitmap? {
    return try {
        val uri = Uri.parse(uriString)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while ((bounds.outWidth / sample) > 2048 || (bounds.outHeight / sample) > 2048) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Renderiza el recorte cuadrado: la misma transformación que la vista
 * previa (cubrir el cuadro, escala y desplazamiento del usuario).
 */
private fun renderCropped(
    src: Bitmap,
    scale: Float,
    offset: Offset,
    previewPx: Float,
    outSize: Int
): Bitmap? {
    if (previewPx <= 0f) return null
    return try {
        val cover = max(outSize / src.width.toFloat(), outSize / src.height.toFloat())
        val total = cover * scale
        val ox = offset.x * (outSize / previewPx)
        val oy = offset.y * (outSize / previewPx)
        val matrix = Matrix().apply {
            postTranslate(-src.width / 2f, -src.height / 2f)
            postScale(total, total)
            postTranslate(outSize / 2f + ox, outSize / 2f + oy)
        }
        val out = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint().apply { isFilterBitmap = true }
        canvas.drawBitmap(src, matrix, paint)
        out
    } catch (e: Exception) {
        null
    }
}

/** Guarda el recorte en archivos de la app y devuelve su URI (null si falla). */
internal suspend fun saveCroppedCover(
    context: Context,
    playlistId: String,
    bitmap: Bitmap
): String? = withContext(Dispatchers.IO) {
    try {
        val dir = File(context.filesDir, "playlist_covers").apply { mkdirs() }
        val safeId = playlistId.map {
            if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_'
        }.joinToString("")
        val out = File(dir, "$safeId.jpg")
        FileOutputStream(out).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }
        Uri.fromFile(out).toString()
    } catch (e: Exception) {
        null
    }
}

@Composable
fun CropImageDialog(
    imageUri: String,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var previewPx by remember { mutableStateOf(0f) }
    var rendering by remember { mutableStateOf(false) }

    LaunchedEffect(imageUri) {
        source = withContext(Dispatchers.IO) { decodeSampledBitmap(context, imageUri) }
        loadFailed = source == null
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    "Recorta la portada",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                val bmp = source
                if (bmp == null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (loadFailed) {
                            Text("No se pudo cargar la imagen.")
                        } else {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    BoxWithConstraints(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .onSizeChanged { previewPx = it.width.toFloat() }
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                    ) {
                        // Topes para no dejar huecos: el cuadro siempre cubierto
                        fun maxOffsetX(s: Float): Float {
                            val cover = max(previewPx / bmp.width, previewPx / bmp.height)
                            return max(0f, (bmp.width * cover * s - previewPx) / 2f)
                        }
                        fun maxOffsetY(s: Float): Float {
                            val cover = max(previewPx / bmp.width, previewPx / bmp.height)
                            return max(0f, (bmp.height * cover * s - previewPx) / 2f)
                        }
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                }
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (scale * zoom).coerceIn(1f, 4f)
                                        scale = newScale
                                        offset = Offset(
                                            (offset.x + pan.x).coerceIn(
                                                -maxOffsetX(newScale),
                                                maxOffsetX(newScale)
                                            ),
                                            (offset.y + pan.y).coerceIn(
                                                -maxOffsetY(newScale),
                                                maxOffsetY(newScale)
                                            )
                                        )
                                    }
                                }
                        )
                        // Grid de tercios sobre la preview
                        Canvas(Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            for (i in 1..2) {
                                drawLine(
                                    Color.White.copy(alpha = 0.6f),
                                    Offset(w * i / 3f, 0f),
                                    Offset(w * i / 3f, h),
                                    strokeWidth = 2f
                                )
                                drawLine(
                                    Color.White.copy(alpha = 0.6f),
                                    Offset(0f, h * i / 3f),
                                    Offset(w, h * i / 3f),
                                    strokeWidth = 2f
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Arrastra para mover y pellizca para acercar. La imagen queda cuadrada (1:1).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancelar") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = !rendering,
                            onClick = {
                                rendering = true
                                scope.launch {
                                    val out = withContext(Dispatchers.Default) {
                                        renderCropped(bmp, scale, offset, previewPx, 1080)
                                    }
                                    rendering = false
                                    if (out != null) onConfirm(out)
                                }
                            }
                        ) {
                            if (rendering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Usar esta imagen")
                            }
                        }
                    }
                }
            }
        }
    }
}
