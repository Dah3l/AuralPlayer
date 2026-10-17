// Diálogo para editar metadatos de una canción (título, artista, álbum, portada).
package com.aural.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aural.player.data.CoverArtClient
import com.aural.player.data.CoverArtResult
import com.aural.player.data.MetadataEditor
import com.aural.player.data.SongMetadata
import kotlinx.coroutines.launch
import android.app.Activity
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun EditMetadataDialog(
    songUri: String,
    initialTitle: String,
    initialArtist: String,
    initialAlbum: String,
    currentCoverUri: String?,
    onSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(initialTitle) }
    var artist by remember { mutableStateOf(initialArtist) }
    var album by remember { mutableStateOf(initialAlbum) }
    var coverUri by remember { mutableStateOf(currentCoverUri) }
    var showCoverSearch by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    // Cargar metadatos actuales
    LaunchedEffect(songUri) {
        MetadataEditor.read(context, songUri)?.let { meta ->
            title = meta.title.ifBlank { initialTitle }
            artist = meta.artist.ifBlank { initialArtist }
            album = meta.album.ifBlank { initialAlbum }
        }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar información") },
        text = {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Portada actual (tocar para cambiar)
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .align(Alignment.CenterHorizontally)
                            .clickable { showCoverSearch = true },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = coverUri,
                            contentDescription = "Portada",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    TextButton(
                        onClick = { showCoverSearch = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Cambiar portada")
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        label = { Text("Artista") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = album,
                        onValueChange = { album = it },
                        label = { Text("Álbum") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                }
            }
        },
        confirmButton = {
            // Launcher para pedir permiso de escritura en Android 11+
            val writeRequestLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    // Permiso concedido, reintentar guardado
                    scope.launch {
                        isSaving = true
                        val success = MetadataEditor.write(
                            context = context,
                            songUri = songUri,
                            metadata = SongMetadata(
                                title = title,
                                artist = artist,
                                album = album
                            )
                        )
                        isSaving = false
                        if (success) onSaved() else onDismiss()
                    }
                } else {
                    isSaving = false
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        try {
                            val success = MetadataEditor.write(
                                context = context,
                                songUri = songUri,
                                metadata = SongMetadata(
                                    title = title,
                                    artist = artist,
                                    album = album
                                )
                            )
                            isSaving = false
                            if (success) onSaved() else onDismiss()
                        } catch (e: SecurityException) {
                            // Necesita permiso de escritura (Android 11+)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    val uri = android.net.Uri.parse(songUri)
                                    val request = MediaStore.createWriteRequest(
                                        context.contentResolver,
                                        listOf(uri)
                                    )
                                    val intentSenderRequest = IntentSenderRequest.Builder(
                                        request.intentSender
                                    ).build()
                                    writeRequestLauncher.launch(intentSenderRequest)
                                } catch (ex: Exception) {
                                    isSaving = false
                                    onDismiss()
                                }
                            } else {
                                isSaving = false
                                onDismiss()
                            }
                        }
                    }
                },
                enabled = !isSaving && !isLoading
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("Guardar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )

    // Sub-diálogo para buscar portada
    if (showCoverSearch) {
        CoverPickerDialog(
            artist = artist,
            title = title,
            hasCustomCover = false,
            onSelect = { url ->
                coverUri = url
                showCoverSearch = false
            },
            onPickFromGallery = {
                // TODO: gallery picker
                showCoverSearch = false
            },
            onRemove = {
                coverUri = null
                showCoverSearch = false
            },
            onDismiss = { showCoverSearch = false }
        )
    }
}
