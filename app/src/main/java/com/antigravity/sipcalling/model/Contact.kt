package com.antigravity.sipcalling.model

data class Contact(
    val id: Long = 0,
    val name: String,
    val phoneNumber: String, // Normalized starting with '0'
    val email: String = "",
    val isStarred: Boolean = false
) {
    val initials: String
        get() {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return "#"
            val parts = trimmed.split("\\s+".toRegex())
            return if (parts.size >= 2) {
                "${parts[0].firstOrNull() ?: ""}${parts[1].firstOrNull() ?: ""}".uppercase()
            } else {
                trimmed.take(2).uppercase()
            }
        }
}
