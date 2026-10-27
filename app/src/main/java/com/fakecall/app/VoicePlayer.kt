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

/**
 * [pitchRangeHz] is what the pitch slider covers. The female range spans Indian women's
 * voices (typically averaging about 180–250 Hz) with room on either side.
 * [typicalPitchHz] stands in for a voice whose own pitch hasn't been measured.
 */
enum class VoiceGender(
    val pitchRangeHz: ClosedFloatingPointRange<Float>,
    val defaultPitchHz: Float,
    val typicalPitchHz: Float,
) {
    FEMALE(150f..320f, defaultPitchHz = 220f, typicalPitchHz = 210f),
    MALE(85f..180f, defaultPitchHz = 125f, typicalPitchHz = 130f),
}

/**
 * Which voice reads the message: [variant] picks one of the phone's voices of that gender,
 * shifted up or down so it speaks at [pitchHz].
 */
data class VoiceStyle(
    val gender: VoiceGender = VoiceGender.FEMALE,
    val variant: Int = 0,
    val pitchHz: Float = gender.defaultPitchHz,
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

    /** The measured natural pitch of the voice [style] uses, or null if it hasn't been measured. */
    fun naturalPitchHz(style: VoiceStyle): Float? =
        if (ready) voiceFor(style)?.let { knownVoice(it)?.pitchHz } else null

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
        val voice = voiceFor(style)
        val naturalHz = if (voice != null) {
            tts.voice = voice
            knownVoice(voice)?.pitchHz ?: style.gender.typicalPitchHz
        } else {
            // No voice is known to be male or female: shift the pitch of the default voice instead.
            tts.language = Locale.getDefault()
            DEFAULT_VOICE_PITCH_HZ
        }
        // The engine's pitch setting multiplies the voice's own pitch.
        tts.setPitch(style.pitchHz / naturalHz)
        Log.d(TAG, "${voice?.name ?: "default voice"}: ${naturalHz.toInt()} Hz -> ${style.pitchHz.toInt()} Hz")
    }

    private fun voiceFor(style: VoiceStyle): Voice? {
        val voices = candidates(style.gender)
        return voices.getOrNull(style.variant) ?: voices.firstOrNull()
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
        return knownVoice(voice)?.gender
    }

    /** Google voices are named like "en-us-x-iom-local"; the three letters identify the speaker. */
    private fun knownVoice(voice: Voice): KnownVoice? =
        GOOGLE_VOICES[voice.name.lowercase(Locale.ROOT).substringAfter("-x-", "").substringBefore("-")]

    /** [pitchHz] is the voice's measured natural pitch, where known. */
    private class KnownVoice(val gender: VoiceGender, val pitchHz: Float? = null)

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

        /** Phones' default voices are usually female, at roughly this pitch. */
        const val DEFAULT_VOICE_PITCH_HZ = 200f

        val SAMSUNG_VOICE = Regex("smt([fm])\\d")

        private val FEMALE = VoiceGender.FEMALE
        private val MALE = VoiceGender.MALE

        // Pitches were measured from samples rendered on a Samsung Galaxy M51 (Google TTS); the
        // engine's pitch setting was checked to scale these linearly from 0.6x to 2.4x.
        val GOOGLE_VOICES = mapOf(
            // English (US)
            "sfg" to KnownVoice(FEMALE, 216f), "iob" to KnownVoice(FEMALE, 194f),
            "iog" to KnownVoice(FEMALE, 226f), "tpf" to KnownVoice(FEMALE, 198f),
            "iol" to KnownVoice(MALE, 138f), "iom" to KnownVoice(MALE, 134f), "tpd" to KnownVoice(MALE, 136f),
            // English (UK)
            "gba" to KnownVoice(FEMALE), "gbc" to KnownVoice(FEMALE), "gbg" to KnownVoice(FEMALE),
            "gbb" to KnownVoice(MALE), "gbd" to KnownVoice(MALE), "rjs" to KnownVoice(MALE),
            // English (India)
            "ena" to KnownVoice(FEMALE, 237f), "enc" to KnownVoice(FEMALE, 276f),
            "ene" to KnownVoice(FEMALE, 182f), "ahp" to KnownVoice(FEMALE), "cxx" to KnownVoice(FEMALE),
            "end" to KnownVoice(MALE, 139f),
            // English (Australia)
            "aua" to KnownVoice(FEMALE), "auc" to KnownVoice(FEMALE),
            "aub" to KnownVoice(MALE), "aud" to KnownVoice(MALE),
            // Hindi
            "hia" to KnownVoice(FEMALE), "hic" to KnownVoice(FEMALE),
            "hid" to KnownVoice(MALE), "hie" to KnownVoice(MALE),
        )
    }
}
