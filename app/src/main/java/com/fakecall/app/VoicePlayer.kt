package com.fakecall.app

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

enum class VoiceGender { FEMALE, MALE }

/** Which voice reads the message: [variant] picks one of the phone's voices of that gender. */
data class VoiceStyle(
    val gender: VoiceGender = VoiceGender.FEMALE,
    val variant: Int = 0,
    val pitch: Float = 1f,
)

/** Reads the caller's message aloud with the phone's built-in text-to-speech. */
class VoicePlayer(context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var ready = false
    private var whenReady: (() -> Unit)? = null
    private var loopText: String? = null
    private val _isReady = MutableStateFlow(false)

    /** True once the speech engine has started and its voices can be listed. */
    val isReady: StateFlow<Boolean> = _isReady

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, { status ->
        handler.post {
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts.setOnUtteranceProgressListener(progressListener)
                _isReady.value = true
                whenReady?.invoke()
            }
            whenReady = null
        }
    }, preferredEngine(context))

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            if (utteranceId == LOOP_ID) handler.post { scheduleNext(PAUSE_MS) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = onDone(utteranceId)
    }

    /** Plays the message once through the loudspeaker so it can be checked before the call. */
    fun preview(text: String, style: VoiceStyle) = runWhenReady {
        stop()
        configure(style, AudioAttributes.USAGE_MEDIA)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, PREVIEW_ID)
    }

    /** Repeats the message with a short pause in between, like someone talking on the line. */
    fun startLoop(text: String, style: VoiceStyle) = runWhenReady {
        stop()
        configure(style, AudioAttributes.USAGE_VOICE_COMMUNICATION)
        loopText = text
        scheduleNext(FIRST_DELAY_MS)
    }

    /** How many different voices of [gender] can be chosen from. */
    fun voiceCount(gender: VoiceGender): Int = if (ready) candidates(gender).size.coerceAtLeast(1) else 1

    fun stop() {
        whenReady = null
        loopText = null
        handler.removeCallbacksAndMessages(null)
        if (ready) tts.stop()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private fun runWhenReady(action: () -> Unit) {
        if (ready) action() else whenReady = action
    }

    private fun scheduleNext(delayMs: Long) {
        val text = loopText ?: return
        handler.postDelayed({ tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, LOOP_ID) }, delayMs)
    }

    private fun configure(style: VoiceStyle, usage: Int) {
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        val voices = candidates(style.gender)
        val voice = voices.getOrNull(style.variant) ?: voices.firstOrNull()
        Log.d(TAG, "${style.gender} voice: ${voice?.name ?: "default voice, pitch shifted"}, pitch ${style.pitch}")
        if (voice != null) {
            tts.voice = voice
            tts.setPitch(style.pitch)
        } else {
            // No voice is known to be male or female: shift the pitch of the default voice instead.
            tts.language = Locale.getDefault()
            tts.setPitch(style.pitch * if (style.gender == VoiceGender.FEMALE) 1.15f else 0.7f)
        }
    }

    private fun availableVoices(): List<Voice> =
        runCatching { tts.voices }.getOrNull().orEmpty()
            .filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }

    /** Voices of [gender] in the phone's language (or English), the phone's own country first. */
    private fun candidates(gender: VoiceGender): List<Voice> {
        val locale = Locale.getDefault()
        val voices = availableVoices().filter { genderOf(it) == gender }
        val sameLanguage = voices.filter { it.locale.language == locale.language }
            .ifEmpty { voices.filter { it.locale.language == "en" } }
        // Google lists each speaker twice (offline and online): keep one, preferring offline.
        return sameLanguage
            .groupBy { it.name.removeSuffix("-local").removeSuffix("-network") }
            .values
            .map { copies -> copies.minBy { it.isNetworkConnectionRequired } }
            .sortedWith(compareBy<Voice>({ it.locale.country != locale.country }, { it.name }))
            .take(MAX_VARIANTS)
    }

    private fun genderOf(voice: Voice): VoiceGender? {
        val name = voice.name.lowercase(Locale.ROOT)
        val features = voice.features.orEmpty().map { it.lowercase(Locale.ROOT) }
        if ("female" in name || "female" in features) return VoiceGender.FEMALE
        if ("male" in name || "male" in features) return VoiceGender.MALE
        // Samsung voices are named like "en-US-SMTf00" (female) or "en-US-SMTm00" (male).
        SAMSUNG_VOICE.find(name)?.let {
            return if (it.groupValues[1] == "f") VoiceGender.FEMALE else VoiceGender.MALE
        }
        // Google voices are named like "en-us-x-iom-local"; the three letters identify the speaker.
        // The English (US and India) entries were checked by measuring each voice's pitch.
        return GOOGLE_VOICES[name.substringAfter("-x-", "").substringBefore("-")]
    }

    private companion object {
        const val TAG = "VoicePlayer"
        const val GOOGLE_ENGINE = "com.google.android.tts"

        /**
         * Google's engine ships both male and female voices, while some phones' own
         * engines (Samsung's, for example) only come with a female one.
         */
        fun preferredEngine(context: Context): String? =
            GOOGLE_ENGINE.takeIf { runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess }
        const val LOOP_ID = "loop"
        const val PREVIEW_ID = "preview"
        const val FIRST_DELAY_MS = 1_200L
        const val PAUSE_MS = 2_500L
        const val MAX_VARIANTS = 4

        val SAMSUNG_VOICE = Regex("smt([fm])\\d")

        val GOOGLE_VOICES = mapOf(
            // English (US)
            "sfg" to VoiceGender.FEMALE, "iob" to VoiceGender.FEMALE, "iog" to VoiceGender.FEMALE,
            "tpf" to VoiceGender.FEMALE,
            "iol" to VoiceGender.MALE, "iom" to VoiceGender.MALE, "tpd" to VoiceGender.MALE,
            // English (UK)
            "gba" to VoiceGender.FEMALE, "gbc" to VoiceGender.FEMALE, "gbg" to VoiceGender.FEMALE,
            "gbb" to VoiceGender.MALE, "gbd" to VoiceGender.MALE, "rjs" to VoiceGender.MALE,
            // English (India)
            "ena" to VoiceGender.FEMALE, "enc" to VoiceGender.FEMALE, "ene" to VoiceGender.FEMALE,
            "ahp" to VoiceGender.FEMALE, "cxx" to VoiceGender.FEMALE,
            "end" to VoiceGender.MALE,
            // English (Australia)
            "aua" to VoiceGender.FEMALE, "auc" to VoiceGender.FEMALE,
            "aub" to VoiceGender.MALE, "aud" to VoiceGender.MALE,
            // Hindi
            "hia" to VoiceGender.FEMALE, "hic" to VoiceGender.FEMALE,
            "hid" to VoiceGender.MALE, "hie" to VoiceGender.MALE,
        )
    }
}
