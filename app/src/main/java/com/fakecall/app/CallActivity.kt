package com.fakecall.app

import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

/** The incoming call and in-call screen. It also shows on top of the lock screen. */
class CallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        showOverLockScreen()
        // Volume buttons adjust the call voice, as in a real call.
        volumeControlStream = AudioManager.STREAM_VOICE_CALL
        handleAction(intent)

        setContent {
            val session by CallState.session.collectAsState()
            val current = session
            if (current == null || current.phase == Phase.WAITING) {
                LaunchedEffect(Unit) { finish() }
            } else {
                CallScreen(
                    session = current,
                    onAnswer = { CallService.send(this, CallService.ACTION_ANSWER) },
                    onEnd = { CallService.send(this, CallService.ACTION_END) },
                    onToggleSpeaker = { CallService.send(this, CallService.ACTION_TOGGLE_SPEAKER) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAction(intent)
    }

    /** "Answer" tapped on the notification opens this screen with this action. */
    private fun handleAction(intent: Intent?) {
        if (intent?.action == ACTION_ANSWER) CallService.send(this, CallService.ACTION_ANSWER)
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        const val ACTION_ANSWER = "com.fakecall.app.action.ANSWER_FROM_NOTIFICATION"
    }
}

private val AcceptGreen = Color(0xFF2ECC71)
private val DeclineRed = Color(0xFFE53935)

@Composable
private fun CallScreen(
    session: CallSession,
    onAnswer: () -> Unit,
    onEnd: () -> Unit,
    onToggleSpeaker: () -> Unit,
) {
    val caller = session.caller
    val fullScreenPhoto = caller.photoStyle == PhotoStyle.FULL_SCREEN && caller.photoPath != null
    val photo by rememberPhoto(caller.photoPath, maxPx = if (fullScreenPhoto) 1440 else 512)
    val ringing = session.phase == Phase.RINGING

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF243B55), Color(0xFF141E30)))),
    ) {
        val backdrop = photo
        if (fullScreenPhoto && backdrop != null) {
            Image(
                bitmap = backdrop.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // Darkens the top and bottom so the name and buttons stay readable on any photo.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.6f),
                            0.35f to Color.Transparent,
                            0.6f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.75f),
                        ),
                    ),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Text(
                if (ringing) "Incoming call" else callTimer(session.answeredAt),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(24.dp))
            if (!fullScreenPhoto) {
                Avatar(photo, size = 132.dp, background = Color.White.copy(alpha = 0.15f), iconTint = Color.White)
                Spacer(Modifier.height(24.dp))
            }
            Text(
                caller.displayName,
                color = Color.White,
                fontSize = 32.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            if (caller.name.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(caller.number, color = Color.White.copy(alpha = 0.75f), fontSize = 18.sp)
            }
            Spacer(Modifier.weight(1f))
            if (ringing) RingingControls(onAnswer, onEnd) else InCallControls(session.speakerOn, onToggleSpeaker, onEnd)
        }
    }
}

@Composable
private fun callTimer(since: Long): String {
    val now by produceState(System.currentTimeMillis(), since) {
        while (true) {
            value = System.currentTimeMillis()
            delay(500)
        }
    }
    val total = ((now - since) / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
}

@Composable
private fun RingingControls(onAnswer: () -> Unit, onDecline: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "scale",
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        RoundButton(Icons.Filled.CallEnd, "Decline", DeclineRed, onDecline)
        RoundButton(Icons.Filled.Call, "Accept", AcceptGreen, onAnswer, Modifier.scale(pulse))
    }
}

/** Speaker switches where the voice message plays; mute and keypad only change their look. */
@Composable
private fun InCallControls(speakerOn: Boolean, onToggleSpeaker: () -> Unit, onEnd: () -> Unit) {
    var muted by remember { mutableStateOf(false) }
    var keypad by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(40.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            CallToggle(Icons.Filled.MicOff, "Mute", muted) { muted = !muted }
            CallToggle(Icons.Filled.Dialpad, "Keypad", keypad) { keypad = !keypad }
            CallToggle(Icons.AutoMirrored.Filled.VolumeUp, "Speaker", speakerOn, onToggleSpeaker)
        }
        RoundButton(Icons.Filled.CallEnd, "End", DeclineRed, onEnd)
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun CallToggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (on) Color.White else Color.White.copy(alpha = 0.15f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (on) Color.Black else Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
    }
}
