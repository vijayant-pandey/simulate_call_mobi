package com.fakecall.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Adds a text message to the phone's Messages app, unread, dated at any day and time. */
@Composable
fun SimulateSmsSection(isDefaultSmsApp: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var message by rememberSaveable { mutableStateOf("") }
    var senderName by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    var countryIso by rememberSaveable { mutableStateOf(Countries.guessIso(context)) }
    var sentAt by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var messageError by rememberSaveable { mutableStateOf(false) }
    var numberError by rememberSaveable { mutableStateOf(false) }

    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        scope.launch {
            val contact = withContext(Dispatchers.IO) { ContactReader.read(context, uri) }
            if (contact == null) {
                Toast.makeText(context, "Couldn't read that contact", Toast.LENGTH_SHORT).show()
                return@launch
            }
            senderName = contact.name
            number = contact.number
            numberError = false
        }
    }

    fun addMessage(becomeSmsApp: () -> Unit) {
        val fullNumber = PhoneNumbers.withCountryCode(Countries.byIso(countryIso).code, number)
        messageError = message.isBlank()
        numberError = fullNumber == null
        if (messageError || fullNumber == null) return
        if (!SmsWriter.isDefaultSmsApp(context)) return becomeSmsApp()

        scope.launch {
            val added = withContext(Dispatchers.IO) {
                SmsWriter.addToInbox(context, fullNumber, message.trim(), sentAt)
            }
            if (!added) {
                Toast.makeText(context, "Couldn't add the SMS", Toast.LENGTH_SHORT).show()
                return@launch
            }
            Toast.makeText(context, "SMS added. Now set Messages as your SMS app again.", Toast.LENGTH_LONG).show()
            message = ""
            runCatching { context.startActivity(SmsWriter.switchBackIntent(context)) }
        }
    }

    val smsAppRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (SmsWriter.isDefaultSmsApp(context)) {
            addMessage(becomeSmsApp = {})
        } else {
            Toast.makeText(
                context,
                "SMS not added: Simulate Call has to be the SMS app for a moment",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Column {
            Text("Simulate SMS", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Adds an unread text to your Messages app, at any date and time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isDefaultSmsApp) {
            SwitchBackCard(onSwitchBack = {
                runCatching { context.startActivity(SmsWriter.switchBackIntent(context)) }
            })
        }

        OutlinedTextField(
            value = message,
            onValueChange = {
                message = it
                messageError = false
            },
            label = { Text("Message") },
            isError = messageError,
            supportingText = if (messageError) ({ Text("Enter the message text") }) else null,
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedButton(
            onClick = {
                try {
                    contactPicker.launch(ContactReader.pickIntent())
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, "No contacts app found", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Contacts, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (senderName.isBlank()) "Choose sender from contacts" else "From: $senderName")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountryCodeField(Countries.byIso(countryIso), onSelect = { countryIso = it.iso })
            OutlinedTextField(
                value = number,
                onValueChange = {
                    number = it
                    senderName = ""
                    numberError = false
                },
                label = { Text("Sender number") },
                isError = numberError,
                supportingText = { Text(if (numberError) "Enter a phone number" else "Or type the full number starting with +") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        SentAtPicker(sentAt, onChange = { sentAt = it })

        Button(
            onClick = {
                addMessage(becomeSmsApp = {
                    SmsWriter.rememberCurrentSmsApp(context)
                    smsAppRequest.launch(SmsWriter.requestDefaultIntent(context))
                })
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Send SMS", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Date and time the message shows as received; past, present or future. */
@Composable
private fun SentAtPicker(sentAt: Long, onChange: (Long) -> Unit) {
    val context = LocalContext.current
    val date = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(sentAt))
    val time = DateFormat.getTimeFormat(context).format(Date(sentAt))
    fun calendar() = Calendar.getInstance().apply { timeInMillis = sentAt }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Received at", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onChange(System.currentTimeMillis()) }) { Text("Now") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val cal = calendar()
                    DatePickerDialog(
                        context,
                        { _, year, month, day -> onChange(cal.apply { set(year, month, day) }.timeInMillis) },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH),
                    ).show()
                },
                modifier = Modifier.weight(1.4f),
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(date, maxLines = 1)
            }
            OutlinedButton(
                onClick = {
                    val cal = calendar()
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            cal.set(Calendar.HOUR_OF_DAY, hour)
                            cal.set(Calendar.MINUTE, minute)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            onChange(cal.timeInMillis)
                        },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        DateFormat.is24HourFormat(context),
                    ).show()
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.AccessTime, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(time, maxLines = 1)
            }
        }
    }
}

/** Shown while this app is still the SMS app, so real texts keep going to Messages. */
@Composable
private fun SwitchBackCard(onSwitchBack: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Simulate Call is your SMS app right now", style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose Messages as your SMS app again so your texts keep going there.",
                style = MaterialTheme.typography.bodyMedium,
            )
            FilledTonalButton(onClick = onSwitchBack) { Text("Switch back to Messages") }
        }
    }
}
