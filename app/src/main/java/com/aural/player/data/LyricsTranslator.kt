// Traducción de letras en el teléfono (ML Kit Translate — sin internet tras bajar los modelos)
package com.aural.player.data

import android.content.Context
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale

object LyricsTranslator {

    const val TARGET_TAG = "es"

    /** Tamaño aproximado de cada modelo de idioma (ML Kit no expone el tamaño real antes de bajar). */
    const val MODEL_SIZE_MB_EACH = 30

    /**
     * Detecta el idioma de un texto. Devuelve el tag BCP-47 ("en", "pt"...)
     * o null si no se pudo identificar.
     */
    suspend fun detectLanguage(text: String): String? = withContext(Dispatchers.IO) {
        try {
            val sample = text.take(1000)
            if (sample.isBlank()) return@withContext null
            val identifier = LanguageIdentification.getClient()
            try {
                val tag = identifier.identifyLanguage(sample).await()
                if (tag == "und") null else tag
            } finally {
                identifier.close()
            }
        } catch (e: Exception) { null }
    }

    /** true si ML Kit puede traducir desde ese idioma. */
    fun isSupported(tag: String): Boolean {
        return try {
            TranslateLanguage.fromLanguageTag(tag) != null
        } catch (e: Exception) { false }
    }

    /** Nombre del idioma en español ("inglés", "portugués"...), o el tag si no se sabe. */
    fun displayName(tag: String): String {
        return try {
            Locale.forLanguageTag(tag).getDisplayLanguage(Locale("es"))
                ?.takeIf { it.isNotBlank() } ?: tag
        } catch (e: Exception) { tag }
    }

    /** true si los modelos de origen y de español ya están descargados. */
    suspend fun areModelsReady(sourceTag: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val models = RemoteModelManager.getInstance()
                .getDownloadedModels(TranslateRemoteModel::class.java).await()
            val langs = models.map { it.language }.toSet()
            langs.contains(sourceTag) && langs.contains(TARGET_TAG)
        } catch (e: Exception) { false }
    }

    /** Descarga los modelos necesarios (no hace nada si ya están). */
    suspend fun downloadModels(sourceTag: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val translator = newTranslator(sourceTag) ?: return@withContext false
            try {
                translator.downloadModelIfNeeded().await()
                true
            } finally {
                translator.close()
            }
        } catch (e: Exception) { false }
    }

    /**
     * Traduce línea por línea (así la traducción queda alineada con el LRC).
     * Devuelve null si algo falla. onProgress(done, total).
     */
    suspend fun translateLines(
        lines: List<String>,
        sourceTag: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<String>? = withContext(Dispatchers.IO) {
        try {
            val translator = newTranslator(sourceTag) ?: return@withContext null
            try {
                val out = ArrayList<String>(lines.size)
                for ((i, line) in lines.withIndex()) {
                    if (line.isBlank()) out.add(line)
                    else out.add(translator.translate(line).await())
                    onProgress(i + 1, lines.size)
                }
                out
            } finally {
                translator.close()
            }
        } catch (e: Exception) { null }
    }

    private fun newTranslator(sourceTag: String): Translator? {
        val source = try {
            TranslateLanguage.fromLanguageTag(sourceTag)
        } catch (e: Exception) { null } ?: return null
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(TranslateLanguage.SPANISH)
            .build()
        return Translation.getClient(options)
    }

    // ---------- Caché de traducciones (por texto + idioma) ----------

    suspend fun getCached(
        context: Context, sourceTag: String, lines: List<String>
    ): List<String>? = withContext(Dispatchers.IO) {
        try {
            val file = cacheFile(context, sourceTag, lines)
            if (!file.exists()) return@withContext null
            val cached = file.readText().split("\n")
            if (cached.size != lines.size) null else cached
        } catch (e: Exception) { null }
    }

    suspend fun putCached(
        context: Context, sourceTag: String, lines: List<String>, translated: List<String>
    ) = withContext(Dispatchers.IO) {
        try {
            if (translated.size != lines.size) return@withContext
            val file = cacheFile(context, sourceTag, lines)
            file.parentFile?.mkdirs()
            file.writeText(translated.joinToString("\n"))
        } catch (e: Exception) { }
    }

    private fun cacheFile(context: Context, sourceTag: String, lines: List<String>): File {
        val key = sourceTag + "\n" + lines.joinToString("\n")
        val hash = MessageDigest.getInstance("SHA-1")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return File(File(context.filesDir, "translations"), "$hash.txt")
    }
}
