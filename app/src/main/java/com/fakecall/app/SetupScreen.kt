package com.fakecall.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val DELAYS = listOf(5, 10, 15, 20, 30, 45, 60, 120, 300)
private val CallGreen = Color(0xFF1E9E55)
private const val PITCH_STEP_HZ = 5f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
    permissions: PermissionStatus,
    isDefaultSmsApp: Boolean,
    onRequestPermissions: () -> Unit,
    onAllowFullScreen: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by CallState.session.collectAsState()

    var name by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    var countryIso by rememberSaveable { mutableStateOf(Countries.guessIso(context)) }
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var photoStyle by rememberSaveable { mutableStateOf(PhotoStyle.CIRCLE) }
    var voiceMessage by rememberSaveable { mutableStateOf("") }
    var voiceGender by rememberSaveable { mutableStateOf(VoiceGender.FEMALE) }
    var voiceVariant by rememberSaveable { mutableIntStateOf(0) }
    var voicePitchHz by rememberSaveable { mutableFloatStateOf(VoiceGender.FEMALE.defaultPitchHz) }
    val voiceStyle = VoiceStyle(voiceGender, voiceVariant, voicePitchHz)
    var delaySeconds by rememberSaveable { mutableIntStateOf(10) }
    var numberError by rememberSaveable { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch { photoPath = withContext(Dispatchers.IO) { PhotoStore.save(context, uri) } }
        }
    }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        scope.launch {
            val contact = withContext(Dispatchers.IO) { ContactReader.read(context, uri) }
            if (contact == null) {
                Toast.makeText(context, "Couldn't read that contact", Toast.LENGTH_SHORT).show()
                return@launch
            }
            name = contact.name
            number = contact.number
            numberError = false
            contact.photoPath?.let { photoPath = it }
        }
    }

    val previewVoice = remember { VoicePlayer(context) }
    DisposableEffect(Unit) {
        onDispose { previewVoice.shutdown() }
    }
    val voicesReady by previewVoice.isReady.collectAsState()

    fun scheduleCall() {
        val fullNumber = PhoneNumbers.withCountryCode(Countries.byIso(countryIso).code, number)
        if (fullNumber == null) {
            numberError = true
            return
        }
        CallService.schedule(context, CallerInfo(name.trim(), fullNumber, photoPath, photoStyle, voiceMessage.trim(), voiceStyle), delaySeconds)
    }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text("Simulate Call", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Pick who's calling and when your phone should ring.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!permissions.allGranted) {
                PermissionCard(permissions, onRequestPermissions, onAllowFullScreen)
            }

            PhotoPicker(
                photoPath = photoPath,
                onPick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemove = { photoPath = null },
            )
            if (photoPath != null) {
                PhotoStyleChooser(photoStyle, onSelect = { photoStyle = it })
            }

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
                Text("Choose from contacts")
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Caller name") },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountryCodeField(Countries.byIso(countryIso), onSelect = { countryIso = it.iso })
                OutlinedTextField(
                    value = number,
                    onValueChange = {
                        number = it
                        numberError = false
                    },
                    label = { Text("Phone number") },
                    isError = numberError,
                    supportingText = {
                        Text(if (numberError) "Enter a phone number" else "Or type the full number starting with +")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            VoiceMessageSection(
                message = voiceMessage,
                onMessageChange = { voiceMessage = it },
                style = voiceStyle,
                voiceCount = if (voicesReady) previewVoice.voiceCount(voiceGender) else 1,
                naturalPitchHz = if (voicesReady) previewVoice.naturalPitchHz(voiceStyle) else null,
                onStyleChange = {
                    voiceGender = it.gender
                    voiceVariant = it.variant
                    voicePitchHz = it.pitchHz
                },
                onPreview = { style ->
                    if (voiceMessage.isNotBlank()) previewVoice.preview(voiceMessage.trim(), style)
                },
            )

            Text("Ring after", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DELAYS.forEach { seconds ->
                    FilterChip(
                        selected = seconds == delaySeconds,
                        onClick = { delaySeconds = seconds },
                        label = { Text(formatDelay(seconds)) },
                    )
                }
            }

            val current = session
            if (current == null) {
                Button(
                    onClick = { scheduleCall() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CallGreen, contentColor = Color.White),
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Schedule call", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                CallStatusCard(
                    session = current,
                    onCancel = { CallService.send(context, CallService.ACTION_END) },
                    onOpen = {
                        context.startActivity(
                            Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
            }

            SimulateSmsSection(isDefaultSmsApp)
        }
    }
}

private fun formatDelay(seconds: Int) = if (seconds < 60) "$seconds sec" else "${seconds / 60} min"

@Composable
private fun PhotoPicker(photoPath: String?, onPick: () -> Unit, onRemove: () -> Unit) {
    val photo by rememberPhoto(photoPath)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Avatar(photo, size = 112.dp, modifier = Modifier.clickable(onClick = onPick))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onPick),
            ) {
                Icon(
                    Icons.Filled.AddAPhoto,
                    contentDescription = "Add photo",
                    modifier = Modifier.padding(8.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        TextButton(onClick = if (photoPath == null) onPick else onRemove) {
            Text(if (photoPath == null) "Add photo" else "Remove photo")
        }
    }
}

@Composable
private fun PhotoStyleChooser(selected: PhotoStyle, onSelect: (PhotoStyle) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Photo on call screen", style = MaterialTheme.typography.titleMedium)
        ChoiceRow(
            options = listOf(PhotoStyle.CIRCLE to "Circle", PhotoStyle.FULL_SCREEN to "Full screen"),
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun VoiceMessageSection(
    message: String,
    onMessageChange: (String) -> Unit,
    style: VoiceStyle,
    voiceCount: Int,
    naturalPitchHz: Float?,
    onStyleChange: (VoiceStyle) -> Unit,
    onPreview: (VoiceStyle) -> Unit,
) {
    // The voice is only heard before the call when the play button is tapped.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Voice message (optional)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = message,
            onValueChange = onMessageChange,
            placeholder = { Text("e.g. Hi, it's me. Please call me back, it's urgent.") },
            supportingText = { Text("Repeated by the caller after you answer") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceRow(
                options = listOf(VoiceGender.FEMALE to "Female", VoiceGender.MALE to "Male"),
                selected = style.gender,
                onSelect = { onStyleChange(VoiceStyle(gender = it)) },
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(onClick = { onPreview(style) }, enabled = message.isNotBlank()) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Hear the voice")
            }
        }
        if (voiceCount > 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Voice", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(56.dp))
                ChoiceRow(
                    options = (0 until voiceCount).map { it to "${it + 1}" },
                    selected = style.variant.coerceAtMost(voiceCount - 1),
                    onSelect = { onStyleChange(style.copy(variant = it)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val range = style.gender.pitchRangeHz
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pitch", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(56.dp))
            Slider(
                value = style.pitchHz.coerceIn(range),
                onValueChange = { onStyleChange(style.copy(pitchHz = it)) },
                valueRange = range,
                steps = ((range.endInclusive - range.start) / PITCH_STEP_HZ).toInt() - 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${style.pitchHz.roundToInt()} Hz",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.End,
                modifier = Modifier.width(64.dp),
            )
        }
        Column(Modifier.padding(start = 56.dp)) {
            val typical = if (style.gender == VoiceGender.FEMALE) "Typical women's voices: 180–250 Hz"
            else "Typical men's voices: 100–150 Hz"
            Text(typical, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (naturalPitchHz != null) {
                Text(
                    "This voice on its own: ${naturalPitchHz.roundToInt()} Hz",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(label)
            }
        }
    }
}

@Composable
fun CountryCodeField(selected: Country, onSelect: (Country) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = "${selected.flag} ${selected.code}",
            onValueChange = {},
            readOnly = true,
            label = { Text("Code") },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.width(132.dp),
        )
        // A read-only text field doesn't react to taps, so this layer opens the menu.
        Box(
            Modifier
                .matchParentSize()
                .clickable { open = true },
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.heightIn(max = 360.dp),
        ) {
            Countries.all.forEach { country ->
                DropdownMenuItem(
                    text = { Text("${country.flag}  ${country.name}  ${country.code}") },
                    onClick = {
                        onSelect(country)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    permissions: PermissionStatus,
    onRequestPermissions: () -> Unit,
    onAllowFullScreen: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Finish setup", style = MaterialTheme.typography.titleMedium)
            if (!permissions.notifications) {
                PermissionRow("Notifications", "Needed to ring while the app is closed.", onRequestPermissions)
            }
            if (!permissions.callLog) {
                PermissionRow("Call history", "Lets simulated calls appear in your phone's call log.", onRequestPermissions)
            }
            if (!permissions.fullScreen) {
                PermissionRow("Full-screen calls", "Shows the call screen over the lock screen.", onAllowFullScreen)
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, detail: String, onAllow: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = onAllow) { Text("Allow") }
    }
}

@Composable
private fun CallStatusCard(session: CallSession, onCancel: () -> Unit, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (session.phase == Phase.WAITING) {
                val now by produceState(System.currentTimeMillis()) {
                    while (true) {
                        value = System.currentTimeMillis()
                        delay(250)
                    }
                }
                val secondsLeft = ((session.ringAt - now + 999) / 1000).coerceAtLeast(0)
                Text(
                    "${session.caller.displayName} calls in $secondsLeft s",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Lock your phone or switch apps — it will ring like a normal call.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            } else {
                Text("Call in progress", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onOpen) { Text("Open call screen") }
            }
        }
    }
}
