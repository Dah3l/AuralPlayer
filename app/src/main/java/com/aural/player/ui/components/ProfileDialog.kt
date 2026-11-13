// Diálogos del perfil: bienvenida (nombre + cumpleaños) y modal de
// cumpleaños. Los datos quedan solo en el teléfono (DataStore local);
// el cumpleaños se guarda sin año (solo día y mes).
package com.aural.player.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** Meses en español, en minúscula (así van en "13 de noviembre"). */
val MONTH_NAMES_ES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
)

/** Días del mes (febrero admite 29: los del 29 también cumplen años). */
private fun daysInMonth(month: Int): Int = when (month) {
    2 -> 29
    4, 6, 9, 11 -> 30
    else -> 31
}

/** "11-13" -> "13 de noviembre". */
fun formatMonthDay(monthDay: String): String {
    val parts = monthDay.split("-")
    if (parts.size != 2) return monthDay
    val month = parts[0].toIntOrNull() ?: return monthDay
    val day = parts[1].toIntOrNull() ?: return monthDay
    if (month !in 1..12) return monthDay
    return "$day de ${MONTH_NAMES_ES[month - 1]}"
}

/**
 * Formulario de nombre + cumpleaños. Se usa en la bienvenida (con "Ahora no")
 * y en Ajustes (con "Borrar mis datos"). El cumpleaños es opcional.
 */
@Composable
fun ProfileFormDialog(
    initialName: String,
    initialBirthday: String?,
    title: String,
    subtitle: String,
    confirmLabel: String,
    showSkip: Boolean,
    showDelete: Boolean,
    onSkip: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (name: String, birthdayMd: String?) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var month by remember {
        mutableStateOf(initialBirthday?.split("-")?.getOrNull(0)?.toIntOrNull())
    }
    var day by remember {
        mutableStateOf(initialBirthday?.split("-")?.getOrNull(1)?.toIntOrNull() ?: 1)
    }
    var monthMenu by remember { mutableStateOf(false) }
    var dayMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 30) name = it },
                    label = { Text("Tu nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Tu cumpleaños (opcional)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { monthMenu = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                month?.let {
                                    MONTH_NAMES_ES[it - 1].replaceFirstChar { c -> c.uppercase() }
                                } ?: "Mes"
                            )
                        }
                        DropdownMenu(
                            expanded = monthMenu,
                            onDismissRequest = { monthMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sin fecha") },
                                onClick = { month = null; monthMenu = false }
                            )
                            MONTH_NAMES_ES.forEachIndexed { index, monthName ->
                                DropdownMenuItem(
                                    text = {
                                        Text(monthName.replaceFirstChar { c -> c.uppercase() })
                                    },
                                    onClick = {
                                        month = index + 1
                                        if (day > daysInMonth(index + 1)) {
                                            day = daysInMonth(index + 1)
                                        }
                                        monthMenu = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(0.6f)) {
                        OutlinedButton(
                            onClick = { dayMenu = true },
                            enabled = month != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$day")
                        }
                        DropdownMenu(
                            expanded = dayMenu,
                            onDismissRequest = { dayMenu = false }
                        ) {
                            (1..daysInMonth(month ?: 1)).forEach { d ->
                                DropdownMenuItem(
                                    text = { Text("$d") },
                                    onClick = { day = d; dayMenu = false }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val md = month?.let { "%02d-%02d".format(it, day) }
                    onConfirm(name.trim(), md)
                }
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            Row {
                if (showDelete) {
                    TextButton(onClick = onDelete) {
                        Text(
                            "Borrar mis datos",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (showSkip) {
                    TextButton(onClick = onSkip) { Text("Ahora no") }
                }
            }
        }
    )
}

/** Modal de cumpleaños: mensaje personalizado y detallista, una vez al día. */
@Composable
fun BirthdayModal(
    displayName: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🎂", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "¡Feliz cumpleaños, $displayName!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Hoy es tu día y queremos celebrarlo contigo. " +
                        "Gracias por dejar que AuralPlayer sea parte de tu banda sonora: " +
                        "que este año te traiga mucha música, alegría y momentos que " +
                        "valgan la pena recordar.\n\n" +
                        "Sube el volumen y disfruta tu día.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("¡A celebrar!")
                }
            }
        }
    }
}
