// Cliente de Telegram vía TDLib para la sección de descargas
package com.aural.player.telegram

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/**
 * Estados de autenticación de Telegram.
 */
sealed class TelegramAuthState {
    data object Idle : TelegramAuthState()
    data object WaitingParams : TelegramAuthState()
    data object WaitingPhone : TelegramAuthState()
    data object WaitingCode : TelegramAuthState()
    data object WaitingPassword : TelegramAuthState()
    data object Ready : TelegramAuthState()
    data class Error(val message: String) : TelegramAuthState()
}

/**
 * Gestor de la conexión con Telegram vía TDLib.
 * Maneja autenticación y comunicación con el bot de descargas.
 */
class TelegramManager(private val context: Context) {

    companion object {
        private const val API_ID = 35445093
        private const val API_HASH = "a5f0365ad39624d63c70f9322b4df5d0"""
        private const val BOT_USERNAME = "musicleo23_bot"
    }

    private var client: Client? = null

    private val _authState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Idle)
    val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()

    private val _botResponses = MutableStateFlow<List<BotSong>>(emptyList())
    val botResponses: StateFlow<List<BotSong>> = _botResponses.asStateFlow()

    private var botChatId: Long = 0L

    fun initialize() {
        if (client != null) return
        client = Client.create({ obj -> handleUpdate(obj) }, null, null)
    }

    private fun handleUpdate(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateAuthorizationState -> handleAuthState(obj.authorizationState)
            is TdApi.UpdateNewMessage -> handleNewMessage(obj.message)
            else -> {}
        }
    }

    private fun handleAuthState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                _authState.value = TelegramAuthState.WaitingParams
                val params = TdApi.SetTdlibParameters().apply {
                    apiId = API_ID
                    apiHash = API_HASH
                    databaseDirectory = context.filesDir.absolutePath + "/tdlib"
                    useMessageDatabase = true
                    useSecretChats = false
                    systemLanguageCode = "es"
                    deviceModel = android.os.Build.MODEL
                    applicationVersion = "1.0"
                }
                client?.send(params) {}
            }
            is TdApi.AuthorizationStateWaitPhoneNumber ->
                _authState.value = TelegramAuthState.WaitingPhone
            is TdApi.AuthorizationStateWaitCode ->
                _authState.value = TelegramAuthState.WaitingCode
            is TdApi.AuthorizationStateWaitPassword ->
                _authState.value = TelegramAuthState.WaitingPassword
            is TdApi.AuthorizationStateReady -> {
                _authState.value = TelegramAuthState.Ready
                findBotChat()
            }
            is TdApi.AuthorizationStateClosed ->
                _authState.value = TelegramAuthState.Idle
            else -> {}
        }
    }

    fun sendPhoneNumber(phone: String) {
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null)) {}
    }

    fun sendCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code)) {}
    }

    fun sendPassword(password: String) {
        client?.send(TdApi.CheckAuthenticationPassword(password)) {}
    }

    fun logout() {
        client?.send(TdApi.LogOut()) {}
    }

    private fun findBotChat() {
        client?.send(TdApi.SearchPublicChat(BOT_USERNAME)) { obj ->
            if (obj is TdApi.Chat) {
                botChatId = obj.id
            }
        }
    }

    /**
     * Envía el nombre de una canción al bot y espera respuestas.
     */
    fun searchSong(query: String) {
        if (botChatId == 0L) return
        _botResponses.value = emptyList()
        val content = TdApi.InputMessageText(
            TdApi.FormattedText(query, null), null, false
        )
        client?.send(TdApi.SendMessage(botChatId, null, null, null, null, content)) {}
    }

    private fun handleNewMessage(message: TdApi.Message) {
        if (message.chatId != botChatId) return
        val content = message.content
        if (content is TdApi.MessageAudio) {
            val audio = content.audio
            val song = BotSong(
                messageId = message.id,
                title = audio.title ?: "Sin título",
                performer = audio.performer ?: "Desconocido",
                durationSec = audio.duration,
                fileId = audio.audio.id
            )
            _botResponses.value = _botResponses.value + song
        }
    }

    /**
     * Descarga el archivo de audio de una canción del bot.
     * @return el fileId para observar el progreso vía TDLib
     */
    fun downloadSong(song: BotSong, onComplete: (String?) -> Unit) {
        client?.send(TdApi.DownloadFile(song.fileId, 1, 0, 0, true)) { obj ->
            if (obj is TdApi.File) {
                // Observar hasta que la descarga complete
                observeDownload(obj.id, onComplete)
            } else {
                onComplete(null)
            }
        }
    }

    private fun observeDownload(fileId: Int, onComplete: (String?) -> Unit) {
        client?.send(TdApi.GetFile(fileId)) { obj ->
            if (obj is TdApi.File) {
                if (obj.local.isDownloadingCompleted) {
                    onComplete(obj.local.path)
                } else {
                    // Reintentar en 1 segundo
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        observeDownload(fileId, onComplete)
                    }, 1000)
                }
            } else {
                onComplete(null)
            }
        }
    }

    fun close() {
        client = null
    }
}

/**
 * Canción encontrada vía el bot de Telegram.
 */
data class BotSong(
    val messageId: Long,
    val title: String,
    val performer: String,
    val durationSec: Int,
    val fileId: Int
)
