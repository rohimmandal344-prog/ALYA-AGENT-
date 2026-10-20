package com.example.data.live

/**
 * Explicit connection and lifecycle states for Gemini Live audio conversation.
 */
enum class LiveConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    LISTENING,
    THINKING,
    SPEAKING,
    INTERRUPTED,
    RECONNECTING,
    ERROR,
    DISCONNECTED;

    fun getDisplayName(): String = when (this) {
        IDLE -> "Ready"
        CONNECTING -> "Connecting..."
        CONNECTED -> "Connected"
        LISTENING -> "Listening to you"
        THINKING -> "Thinking..."
        SPEAKING -> "Alya is speaking"
        INTERRUPTED -> "Interrupted"
        RECONNECTING -> "Reconnecting..."
        ERROR -> "Connection Error"
        DISCONNECTED -> "Disconnected"
    }
}
