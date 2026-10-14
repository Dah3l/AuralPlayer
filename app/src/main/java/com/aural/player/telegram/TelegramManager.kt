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
        private const val API_HASH = "a5f0365ad39624d63c70f9322b4df5d0"
        private const val BOT_USERNAME = "musicleo23_bot"
    }

    private var client: Client? = null

    private val _authState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.Idle)
    val authState: StateFlow<TelegramAuthState> = _authState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _botResponses = MutableStateFlow<List<BotSong>>(emptyList())
    val botResponses: StateFlow<List<BotSong>> = _botResponses.asStateFlow()

    private val _botButtons = MutableStateFlow<List<BotButton>>(emptyList())
    val botButtons: StateFlow<List<BotButton>> = _botButtons.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _botStatus = MutableStateFlow("Iniciando...")
    val botStatus: StateFlow<String> = _botStatus.asStateFlow()

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
                    systemLanguageCode = "es"
                    deviceModel = android.os.Build.MODEL
                    systemVersion = android.os.Build.VERSION.RELEASE ?: "13"
                    applicationVersion = "1.0"
                    useTestDc = false
                    databaseDirectory = context.filesDir.absolutePath + "/tdlib/db"
                    filesDirectory = context.filesDir.absolutePath + "/tdlib/files"
                    databaseEncryptionKey = ByteArray(0)
                    useFileDatabase = true
                    useChatInfoDatabase = true
                    useMessageDatabase = true
                    useSecretChats = false
                }
                client?.send(params) {}
            }
            is TdApi.AuthorizationStateWaitPhoneNumber ->
                _authState.value = TelegramAuthState.WaitingPhone
            is TdApi.AuthorizationStateWaitCode -> {
                _isLoading.value = false
                _authState.value = TelegramAuthState.WaitingCode
            }
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
        if (_isLoading.value) return  // Evitar duplicados
        _isLoading.value = true
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null)) { obj ->
            _isLoading.value = false
            if (obj is TdApi.Error) {
                // Si es "another authorization query", no mostrar error, solo reintentar el estado
                if (!obj.message.contains("Another authorization", ignoreCase = true)) {
                    _authState.value = TelegramAuthState.Error("Error: ${obj.message}")
                }
            }
        } ?: run {
            _isLoading.value = false
            _authState.value = TelegramAuthState.Error("Cliente no inicializado")
        }
    }

    fun sendCode(code: String) {
        if (_isLoading.value) return
        _isLoading.value = true
        client?.send(TdApi.CheckAuthenticationCode(code)) { obj ->
            _isLoading.value = false
            if (obj is TdApi.Error) {
                _authState.value = TelegramAuthState.Error("Código incorrecto: ${obj.message}")
            }
        } ?: run { _isLoading.value = false }
    }

    fun sendPassword(password: String) {
        client?.send(TdApi.CheckAuthenticationPassword(password)) {}
    }

    fun logout() {
        client?.send(TdApi.LogOut()) {}
    }

    private fun findBotChat() {
        _botStatus.value = "Buscando bot @$BOT_USERNAME..."
        client?.send(TdApi.SearchPublicChat(BOT_USERNAME)) { obj ->
            android.util.Log.d("TelegramManager", "SearchPublicChat resultado: ${obj.javaClass.simpleName}")
            if (obj is TdApi.Chat) {
                botChatId = obj.id
                _botStatus.value = "Bot encontrado (chat $botChatId)"
                android.util.Log.d("TelegramManager", "Bot chat encontrado: $botChatId")
                client?.send(TdApi.OpenChat(botChatId)) { openResult ->
                    android.util.Log.d("TelegramManager", "OpenChat: $openResult")
                    if (openResult is TdApi.Ok) {
                        _botStatus.value = "Conectado al bot"
                    }
                }
                // Verificar historial inmediatamente
                verifyBotChat()
            } else if (obj is TdApi.Error) {
                _botStatus.value = "Error: ${obj.message}"
                android.util.Log.e("TelegramManager", "Error buscando bot: ${obj.message}")
            } else {
                _botStatus.value = "No se encontró el bot"
            }
        } ?: run {
            _botStatus.value = "Cliente no inicializado"
        }
    }

    /**
     * Verifica que podemos leer el historial del bot.
     */
    private fun verifyBotChat() {
        if (botChatId == 0L) return
        client?.send(TdApi.GetChatHistory(botChatId, 0, 0, 5, false)) { obj ->
            when (obj) {
                is TdApi.Messages -> {
                    _botStatus.value = "Conectado (${obj.totalCount} mensajes en historial)"
                    android.util.Log.d("TelegramManager", "Historial OK: ${obj.messages.size} mensajes")
                }
                is TdApi.Error -> {
                    _botStatus.value = "Error historial: ${obj.message}"
                    android.util.Log.e("TelegramManager", "Error historial: ${obj.message}")
                }
                else -> {
                    _botStatus.value = "Respuesta inesperada"
                }
            }
        }
    }

    fun getBotChatId(): Long = botChatId

    /**
     * Envía el nombre de una canción al bot y espera respuestas.
     */
    /**
     * "Pulsa" un botón inline del bot para que mande el audio.
     */
    fun pressButton(button: BotButton) {
        _botButtons.value = emptyList()
        val payload = TdApi.CallbackQueryPayloadData(button.callbackData)
        client?.send(TdApi.GetCallbackQueryAnswer(button.chatId, button.messageId, payload)) { obj ->
            android.util.Log.d("TelegramManager", "Callback enviado: $obj")
        }
    }

    fun clearButtons() {
        _botButtons.value = emptyList()
    }

    fun searchSong(query: String) {
        if (botChatId == 0L) {
            android.util.Log.w("TelegramManager", "botChatId es 0, buscando de nuevo...")
            findBotChat()
            // Reintentar en 2 segundos
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (botChatId != 0L) searchSong(query)
            }, 2000)
            return
        }
        _botResponses.value = emptyList()
        _botButtons.value = emptyList()
        _isSearching.value = true
        android.util.Log.d("TelegramManager", "Buscando '$query' en chat $botChatId")
        val content = TdApi.InputMessageText(
            TdApi.FormattedText(query, null), null, false
        )
        client?.send(TdApi.SendMessage(botChatId, null, null, null, null, content)) { obj ->
            android.util.Log.d("TelegramManager", "Mensaje enviado: $obj")
            // Polling del historial por si las actualizaciones en tiempo real fallan
            scheduleHistoryCheck(3000)
            scheduleHistoryCheck(7000)
            scheduleHistoryCheck(12000)
            // Timeout: quitar el indicador después de 20s
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                _isSearching.value = false
            }, 20000)
        }
    }

    private fun scheduleHistoryCheck(delayMs: Long) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // Solo buscar si aún no hay botones y seguimos buscando
            if (_botButtons.value.isEmpty() && _isSearching.value) {
                fetchButtonsFromHistory()
            }
        }, delayMs)
    }

    /**
     * Busca botones inline en el historial reciente del chat del bot.
     */
    private fun fetchButtonsFromHistory() {
        if (botChatId == 0L) return
        client?.send(TdApi.GetChatHistory(botChatId, 0, 0, 20, false)) { obj ->
            if (obj is TdApi.Messages) {
                for (msg in obj.messages) {
                    if (msg == null) continue
                    parseButtonsFromMessage(msg)
                    if (_botButtons.value.isNotEmpty()) {
                        _isSearching.value = false
                        break
                    }
                }
                // Si después de revisar no hay botones, seguir buscando
                if (_botButtons.value.isEmpty()) {
                    android.util.Log.d("TelegramManager", "Sin botones en historial, sigue buscando...")
                }
            }
        }
    }

    /**
     * Extrae botones inline de un mensaje (reutilizable).
     */
    private fun parseButtonsFromMessage(message: TdApi.Message) {
        if (message.chatId != botChatId) return
        val markup = message.replyMarkup
        if (markup is TdApi.ReplyMarkupInlineKeyboard) {
            val buttons = mutableListOf<BotButton>()
            for (row in markup.rows) {
                for (btn in row) {
                    val btnType = btn.type
                    if (btnType is TdApi.InlineKeyboardButtonTypeCallback) {
                        buttons.add(
                            BotButton(
                                messageId = message.id,
                                chatId = message.chatId,
                                label = btn.text,
                                callbackData = btnType.data
                            )
                        )
                    }
                }
            }
            if (buttons.isNotEmpty() && _botButtons.value.isEmpty()) {
                _botButtons.value = buttons
                _isSearching.value = false
                android.util.Log.d("TelegramManager", "${buttons.size} botones del historial")
            }
        }
    }

    private fun handleNewMessage(message: TdApi.Message) {
        if (message.chatId != botChatId || botChatId == 0L) return
        android.util.Log.d("TelegramManager", "Nuevo mensaje del bot: ${message.content.javaClass.simpleName}")
        val content = message.content
        when (content) {
            is TdApi.MessageAudio -> {
                val audio = content.audio
                val song = BotSong(
                    messageId = message.id,
                    title = audio.title?.takeIf { it.isNotBlank() } ?: "Sin título",
                    performer = audio.performer?.takeIf { it.isNotBlank() } ?: "Desconocido",
                    durationSec = audio.duration,
                    fileId = audio.audio.id
                )
                _botResponses.value = _botResponses.value + song
                android.util.Log.d("TelegramManager", "Audio agregado: ${song.title}")
            }
            is TdApi.MessageDocument -> {
                val doc = content.document
                val fileName = doc.fileName ?: "Sin título"
                // Solo documentos de audio
                if (doc.mimeType.startsWith("audio/")) {
                    val song = BotSong(
                        messageId = message.id,
                        title = fileName.substringBeforeLast("."),
                        performer = "Desconocido",
                        durationSec = 0,
                        fileId = doc.document.id
                    )
                    _botResponses.value = _botResponses.value + song
                    android.util.Log.d("TelegramManager", "Documento agregado: $fileName")
                }
            }
            is TdApi.MessageText -> {
                // Puede traer botones inline (lista de resultados)
                val markup = message.replyMarkup
                if (markup is TdApi.ReplyMarkupInlineKeyboard) {
                    val buttons = mutableListOf<BotButton>()
                    for (row in markup.rows) {
                        for (btn in row) {
                            val btnType = btn.type
                            if (btnType is TdApi.InlineKeyboardButtonTypeCallback) {
                                buttons.add(
                                    BotButton(
                                        messageId = message.id,
                                        chatId = message.chatId,
                                        label = btn.text,
                                        callbackData = btnType.data
                                    )
                                )
                            }
                        }
                    }
                    if (buttons.isNotEmpty()) {
                        _botButtons.value = buttons
                        _isSearching.value = false
                        android.util.Log.d("TelegramManager", "${buttons.size} botones inline detectados")
                    }
                }
            }
            else -> {
                android.util.Log.d("TelegramManager", "Tipo no manejado: ${content.javaClass.simpleName}")
            }
        }
    }

    /**
     * Descarga el archivo de audio de una canción del bot.
     * @return el fileId para observar el progreso vía TDLib
     */
    fun downloadSong(song: BotSong, onComplete: (Boolean) -> Unit) {
        client?.send(TdApi.DownloadFile(song.fileId, 1, 0, 0, true)) { obj ->
            if (obj is TdApi.File) {
                observeDownload(obj.id, song, onComplete)
            } else {
                onComplete(false)
            }
        }
    }

    private fun observeDownload(fileId: Int, song: BotSong, onComplete: (Boolean) -> Unit) {
        client?.send(TdApi.GetFile(fileId)) { obj ->
            if (obj is TdApi.File) {
                if (obj.local.isDownloadingCompleted) {
                    val saved = saveToMusic(obj.local.path, song)
                    onComplete(saved)
                } else {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        observeDownload(fileId, song, onComplete)
                    }, 1000)
                }
            } else {
                onComplete(false)
            }
        }
    }

    /**
     * Copia el archivo descargado a la carpeta pública de Música y lo escanea.
     */
    private fun saveToMusic(privatePath: String, song: BotSong): Boolean {
        return try {
            val srcFile = java.io.File(privatePath)
            if (!srcFile.exists()) return false

            val fileName = "${song.performer} - ${song.title}.mp3"
                .replace("/", "_").replace("\\", "_").replace(":", "_")

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+: usar MediaStore
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                    put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH, "Music/Aural")
                    put(android.provider.MediaStore.Audio.Media.TITLE, song.title)
                    put(android.provider.MediaStore.Audio.Media.ARTIST, song.performer)
                }
                val uri = context.contentResolver.insert(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    srcFile.inputStream().use { it.copyTo(out) }
                }
            } else {
                // Android 9 y menor: carpeta pública directa
                @Suppress("DEPRECATION")
                val musicDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_MUSIC
                )
                val auralDir = java.io.File(musicDir, "Aural")
                auralDir.mkdirs()
                val destFile = java.io.File(auralDir, fileName)
                srcFile.copyTo(destFile, overwrite = true)
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(destFile.absolutePath), arrayOf("audio/mpeg"), null
                )
            }
            android.util.Log.d("TelegramManager", "Guardado en Música: $fileName")
            true
        } catch (e: Exception) {
            android.util.Log.e("TelegramManager", "Error guardando: ${e.message}")
            false
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

/**
 * Botón inline del bot (lista de resultados antes de mandar el audio).
 */
data class BotButton(
    val messageId: Long,
    val chatId: Long,
    val label: String,
    val callbackData: ByteArray
)
