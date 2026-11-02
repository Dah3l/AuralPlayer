// Aviso de nueva versión: consulta el último release de GitHub y lo
// compara con la versión instalada. Sin backend, sin cuentas y sin
// permisos nuevos: usa la conexión que la app ya tiene.
package com.aural.player.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Release publicado en GitHub. */
data class UpdateInfo(
    val version: String,
    val url: String
)

/** Resultado de comprobar si hay una versión más nueva. */
sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(val info: UpdateInfo) : UpdateCheckResult
    data object Error : UpdateCheckResult
}

private const val RELEASES_URL =
    "https://api.github.com/repos/Dah3l/AuralPlayer/releases/latest"

/** Versión instalada (la del build, p. ej. "1.0.3"). */
fun installedVersionName(context: Context): String {
    return try {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        info.versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }
}

/** true si `latest` es más nueva que `current` ("1.0.3" frente a "1.0.2"). */
fun isNewerVersion(latest: String, current: String): Boolean {
    val latestParts = latest.trim().split(".", "-")
    val currentParts = current.trim().split(".", "-")
    val size = maxOf(latestParts.size, currentParts.size)
    for (i in 0 until size) {
        val l = latestParts.getOrNull(i)?.toIntOrNull() ?: 0
        val c = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
        if (l != c) return l > c
    }
    return false
}

/**
 * Consulta el último release. Si no hay internet, GitHub no responde o
 * el formato cambia, devuelve Error: el llamador no debe mostrar nada
 * en el chequeo automático.
 */
suspend fun checkForUpdate(context: Context): UpdateCheckResult =
    withContext(Dispatchers.IO) {
        try {
            val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "AuralPlayer")
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext UpdateCheckResult.Error
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tag = json.optString("tag_name", "")
                val version = tag.removePrefix("v").removePrefix("V")
                val url = json.optString("html_url", "")
                if (version.isBlank() || url.isBlank()) {
                    return@withContext UpdateCheckResult.Error
                }
                if (isNewerVersion(version, installedVersionName(context))) {
                    UpdateCheckResult.Available(UpdateInfo(version, url))
                } else {
                    UpdateCheckResult.UpToDate
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error
        }
    }
