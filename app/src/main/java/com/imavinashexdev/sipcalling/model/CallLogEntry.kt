package com.imavinashexdev.sipcalling.model

enum class CallType {
    OUTGOING,
    INCOMING,
    MISSED
}

data class CallLogEntry(
    val id: Long = 0,
    val phoneNumber: String,
    val displayName: String = "",
    val type: CallType,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0
) {
    val displayTitle: String
        get() = if (displayName.isNotBlank() && displayName != phoneNumber) displayName else phoneNumber
}
