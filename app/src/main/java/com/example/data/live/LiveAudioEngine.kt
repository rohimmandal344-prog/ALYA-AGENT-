package com.example.data.live

import android.content.Context

/**
 * Unified LiveAudioEngine delegating to high-fidelity 16kHz AudioManager.
 */
class LiveAudioEngine(
    context: Context,
    onAudioChunkRecorded: (ByteArray) -> Unit,
    onAmplitudeChanged: (inputRms: Float, outputRms: Float) -> Unit,
    onSpeakingStateChanged: (Boolean) -> Unit
) {
    val audioManager = AudioManager(
        context = context,
        onAudioChunkRecorded = onAudioChunkRecorded,
        onAmplitudeChanged = onAmplitudeChanged,
        onSpeakingStateChanged = onSpeakingStateChanged
    )

    fun startRecording(): Boolean = audioManager.startRecording()

    fun stopRecording() = audioManager.stopRecording()

    fun startPlaybackEngine() = audioManager.startPlayback()

    fun stopPlaybackEngine() = audioManager.stopPlayback()

    fun enqueuePlaybackAudio(pcmData: ByteArray, sourceSampleRate: Int = AudioManager.SAMPLE_RATE_HZ) {
        audioManager.enqueueAudio(pcmData, sourceSampleRate)
    }

    fun stopAndFlushPlayback() {
        audioManager.bargeIn()
    }

    fun release() {
        audioManager.release()
    }
}
