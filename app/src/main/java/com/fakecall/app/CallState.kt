package com.fakecall.app

import kotlinx.coroutines.flow.MutableStateFlow

enum class PhotoStyle { CIRCLE, FULL_SCREEN }

data class CallerInfo(
    val name: String,
    val number: String,
    val photoPath: String?,
    val photoStyle: PhotoStyle = PhotoStyle.CIRCLE,
    /** What the caller "says" once the call is answered; blank for silence. */
    val voiceMessage: String = "",
    val voiceStyle: VoiceStyle = VoiceStyle(),
) {
    val displayName: String get() = name.ifBlank { number }
}

enum class Phase { WAITING, RINGING, ACTIVE }

data class CallSession(
    val caller: CallerInfo,
    val phase: Phase,
    /** When the phone rings (planned time while waiting, actual time once ringing). */
    val ringAt: Long,
    val answeredAt: Long = 0L,
    val speakerOn: Boolean = false,
)

/** The one fake call in progress, shared by the service and both screens. */
object CallState {
    val session = MutableStateFlow<CallSession?>(null)
}
