// Pantalla de autenticación de Telegram
package com.aural.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aural.player.telegram.TelegramAuthState
import com.aural.player.telegram.TelegramManager

/**
 * Flujo de login de Telegram: teléfono → código SMS → contraseña 2FA.
 */
@Composable
fun TelegramAuthScreen(
    telegramManager: TelegramManager,
    onAuthenticated: () -> Unit,
    onBack: () -> Unit
) {
    val authState by telegramManager.authState.collectAsState()

    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Si ya está autenticado, avisar
    if (authState is TelegramAuthState.Ready) {
        onAuthenticated()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Conectar Telegram",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Para descargar música necesitas vincular tu cuenta de Telegram.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        when (val s = authState) {
            is TelegramAuthState.Idle, is TelegramAuthState.WaitingParams -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Iniciando...", style = MaterialTheme.typography.bodySmall)
            }
            is TelegramAuthState.WaitingPhone -> {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Número de teléfono") },
                    placeholder = { Text("+535...") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { telegramManager.sendPhoneNumber(phone) },
                    enabled = phone.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enviar código")
                }
            }
            is TelegramAuthState.WaitingCode -> {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Código de verificación") },
                    placeholder = { Text("12345") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { telegramManager.sendCode(code) },
                    enabled = code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Verificar")
                }
            }
            is TelegramAuthState.WaitingPassword -> {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña 2FA") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { telegramManager.sendPassword(password) },
                    enabled = password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Confirmar")
                }
            }
            is TelegramAuthState.Error -> {
                Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { telegramManager.initialize() }) {
                    Text("Reintentar")
                }
            }
            else -> {}
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onBack) {
            Text("Volver")
        }
    }
}
