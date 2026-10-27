package com.fakecall.app

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.CallLog
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Runs one fake call from start to finish: waits for the chosen delay, rings,
 * tracks the answered call, and writes the outcome to the phone's call log.
 * It runs as a foreground service so the countdown survives the screen locking.
 */
class CallService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var proximityLock: PowerManager.WakeLock? = null
    private lateinit var voice: VoicePlayer
    /** True while the audio is switched to call mode for the voice message. */
    private var audioRouted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        // Created early so the speech engine is ready by the time the call is answered.
        voice = VoicePlayer(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SCHEDULE -> schedule(intent)
            ACTION_ANSWER -> answer()
            ACTION_END -> end(missed = false)
            ACTION_TOGGLE_SPEAKER -> toggleSpeaker()
            else -> if (CallState.session.value == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopRinging()
        releaseWakeLock()
        stopCallAudio()
        voice.shutdown()
        CallState.session.value = null
        super.onDestroy()
    }

    private fun schedule(intent: Intent) {
        handler.removeCallbacksAndMessages(null)
        stopRinging()

        val voiceGender = VoiceGender.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_VOICE_GENDER) }
            ?: VoiceGender.FEMALE
        val caller = CallerInfo(
            name = intent.getStringExtra(EXTRA_NAME).orEmpty(),
            number = intent.getStringExtra(EXTRA_NUMBER).orEmpty(),
            photoPath = intent.getStringExtra(EXTRA_PHOTO),
            photoStyle = PhotoStyle.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_PHOTO_STYLE) }
                ?: PhotoStyle.CIRCLE,
            voiceMessage = intent.getStringExtra(EXTRA_VOICE_TEXT).orEmpty(),
            voiceStyle = VoiceStyle(
                gender = voiceGender,
                variant = intent.getIntExtra(EXTRA_VOICE_VARIANT, 0),
                pitchHz = intent.getFloatExtra(EXTRA_VOICE_PITCH_HZ, voiceGender.defaultPitchHz),
            ),
        )
        val delayMs = intent.getIntExtra(EXTRA_DELAY_SECONDS, 10) * 1000L
        CallState.session.value = CallSession(caller, Phase.WAITING, System.currentTimeMillis() + delayMs)

        goForeground(Notifications.WAITING_ID, Notifications.waiting(this))
        // Keeps the CPU awake so the countdown isn't paused while the screen is off.
        holdWakeLock(delayMs + RING_TIMEOUT_MS + 10_000)
        handler.postDelayed({ ring() }, delayMs)
    }

    private fun ring() {
        val session = CallState.session.value ?: return stopSelf()
        val ringing = session.copy(phase = Phase.RINGING, ringAt = System.currentTimeMillis())
        CallState.session.value = ringing

        goForeground(Notifications.CALL_ID, Notifications.incoming(this, ringing.caller))
        NotificationManagerCompat.from(this).cancel(Notifications.WAITING_ID)
        // Opens the call screen straight away when the app is on screen; otherwise the
        // full-screen notification above opens it (or shows a heads-up banner).
        runCatching {
            startActivity(Intent(this, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        startRinging()
        handler.postDelayed({ end(missed = true) }, RING_TIMEOUT_MS)
    }

    private fun answer() {
        val session = CallState.session.value
        if (session == null || session.phase != Phase.RINGING) return

        handler.removeCallbacksAndMessages(null)
        stopRinging()
        releaseWakeLock()
        val active = session.copy(phase = Phase.ACTIVE, answeredAt = System.currentTimeMillis())
        CallState.session.value = active
        goForeground(Notifications.CALL_ID, Notifications.ongoing(this, active))
        startCallAudio(active)
    }

    /** Hang up, decline, cancel or time out, depending on where the call is. */
    private fun end(missed: Boolean) {
        val session = CallState.session.value
        handler.removeCallbacksAndMessages(null)
        stopRinging()
        releaseWakeLock()
        stopCallAudio()

        if (session != null && session.phase != Phase.WAITING) {
            val type = when {
                session.phase == Phase.ACTIVE -> CallLog.Calls.INCOMING_TYPE
                missed -> CallLog.Calls.MISSED_TYPE
                else -> CallLog.Calls.REJECTED_TYPE
            }
            val duration = if (session.phase == Phase.ACTIVE) {
                (System.currentTimeMillis() - session.answeredAt) / 1000
            } else {
                0L
            }
            CallLogWriter.write(this, session.caller, session.ringAt, duration, type)
            if (type == CallLog.Calls.MISSED_TYPE) Notifications.showMissed(this, session.caller)
        }

        CallState.session.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Plays the voice message through the earpiece, like the other person talking. */
    private fun startCallAudio(session: CallSession) {
        val message = session.caller.voiceMessage
        if (message.isNotBlank()) {
            getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_COMMUNICATION
            audioRouted = true
            routeToSpeaker(false)
            voice.startLoop(message, session.caller.voiceStyle)
        }
        holdProximityLock()
    }

    private fun stopCallAudio() {
        voice.stop()
        releaseProximityLock()
        if (!audioRouted) return
        val audio = getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= 31) {
            audio.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = false
        }
        audio.mode = AudioManager.MODE_NORMAL
        audioRouted = false
    }

    private fun toggleSpeaker() {
        val session = CallState.session.value
        if (session == null || session.phase != Phase.ACTIVE) return
        val on = !session.speakerOn
        CallState.session.value = session.copy(speakerOn = on)
        if (audioRouted) routeToSpeaker(on)
        // Like a real call, the screen only turns off near your face while the speaker is off.
        if (on) releaseProximityLock() else holdProximityLock()
    }

    private fun routeToSpeaker(on: Boolean) {
        val audio = getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= 31) {
            val type = if (on) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            val device = audio.availableCommunicationDevices.firstOrNull { it.type == type }
            if (device != null) audio.setCommunicationDevice(device) else audio.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = on
        }
    }

    private fun holdProximityLock() {
        if (proximityLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java)
        if (!power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
        proximityLock = power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "FakeCall:proximity")
            .apply { acquire(MAX_CALL_MS) }
    }

    private fun releaseProximityLock() {
        proximityLock?.let { if (it.isHeld) it.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) }
        proximityLock = null
    }

    private fun goForeground(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(id, notification)
        }
    }

    private fun startRinging() {
        val audio = getSystemService(AudioManager::class.java)
        if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            ringtone = RingtoneManager.getRingtone(this, Settings.System.DEFAULT_RINGTONE_URI)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= 28) isLooping = true
                play()
            }
        }
        if (audio.ringerMode != AudioManager.RINGER_MODE_SILENT) {
            vibrator = defaultVibrator().apply {
                vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0))
            }
        }
    }

    private fun stopRinging() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }

    @Suppress("DEPRECATION")
    private fun defaultVibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            getSystemService(Vibrator::class.java)
        }

    private fun holdWakeLock(timeoutMs: Long) {
        releaseWakeLock()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FakeCall:countdown")
            .apply { acquire(timeoutMs) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        private const val ACTION_SCHEDULE = "com.fakecall.app.action.SCHEDULE"
        const val ACTION_ANSWER = "com.fakecall.app.action.ANSWER"
        const val ACTION_END = "com.fakecall.app.action.END"
        const val ACTION_TOGGLE_SPEAKER = "com.fakecall.app.action.TOGGLE_SPEAKER"

        private const val EXTRA_NAME = "name"
        private const val EXTRA_NUMBER = "number"
        private const val EXTRA_PHOTO = "photo"
        private const val EXTRA_PHOTO_STYLE = "photo_style"
        private const val EXTRA_VOICE_TEXT = "voice_text"
        private const val EXTRA_VOICE_GENDER = "voice_gender"
        private const val EXTRA_VOICE_VARIANT = "voice_variant"
        private const val EXTRA_VOICE_PITCH_HZ = "voice_pitch_hz"
        private const val EXTRA_DELAY_SECONDS = "delay"

        /** How long the phone rings before the call counts as missed. */
        private const val RING_TIMEOUT_MS = 30_000L
        private const val MAX_CALL_MS = 2 * 60 * 60 * 1000L

        fun schedule(context: Context, caller: CallerInfo, delaySeconds: Int) {
            val intent = Intent(context, CallService::class.java)
                .setAction(ACTION_SCHEDULE)
                .putExtra(EXTRA_NAME, caller.name)
                .putExtra(EXTRA_NUMBER, caller.number)
                .putExtra(EXTRA_PHOTO, caller.photoPath)
                .putExtra(EXTRA_PHOTO_STYLE, caller.photoStyle.name)
                .putExtra(EXTRA_VOICE_TEXT, caller.voiceMessage)
                .putExtra(EXTRA_VOICE_GENDER, caller.voiceStyle.gender.name)
                .putExtra(EXTRA_VOICE_VARIANT, caller.voiceStyle.variant)
                .putExtra(EXTRA_VOICE_PITCH_HZ, caller.voiceStyle.pitchHz)
                .putExtra(EXTRA_DELAY_SECONDS, delaySeconds)
            ContextCompat.startForegroundService(context, intent)
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, CallService::class.java).setAction(action))
        }
    }
}
