package com.antigravity.sipcalling.model

sealed class RegistrationStatus {
    object Unregistered : RegistrationStatus()
    object Registering : RegistrationStatus()
    data class Registered(val identity: String) : RegistrationStatus()
    data class Failed(val reason: String) : RegistrationStatus()
}

enum class VoipCallState {
    Idle,
    OutgoingInit,
    OutgoingProgress,
    OutgoingRinging,
    IncomingReceived,
    Connected,
    StreamsRunning,
    PausedByRemote,
    Paused,
    Ending,
    Terminated,
    Error
}

data class ActiveCallData(
    val remoteAddress: String,
    val remoteDisplayName: String,
    val state: VoipCallState,
    val durationSeconds: Long = 0,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isOnHold: Boolean = false,
    val codec: String = ""
)
