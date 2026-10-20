package com.example.data.live

import android.content.Context
import android.util.Log

/**
 * High-fidelity AudioManager coordinating native AudioRecord and AudioTrack with 16kHz PCM encoding
 * optimized for the Gemini Live API (gemini-2.0-flash / Gemini Live).
 *
 * Coordinates:
 * - [AudioRecorder]: Low-latency 16kHz capture with AcousticEchoCanceler and NoiseSuppressor.
 * - [AudioTrackManager]: Low-latency 16kHz playback with thread-safe generational buffer queue and instant barge-in.
 */
class AudioManager(
    context: Context,
    private val onAudioChunkRecorded: (ByteArray) -> Unit,
    private val onAmplitudeChanged: (inputRms: Float, outputRms: Float) -> Unit,
    private val onSpeakingStateChanged: (Boolean) -> Unit
) {
    companion object {
        private const val TAG = "AudioManager"
        const val SAMPLE_RATE_HZ = 16000
    }

    private var currentInputRms: Float = 0f
    private var currentOutputRms: Float = 0f

    val recorder = AudioRecorder(
        context = context,
        onAudioChunkRecorded = onAudioChunkRecorded,
        onAmplitudeChanged = { inRms ->
            currentInputRms = inRms
            onAmplitudeChanged(inRms, currentOutputRms)
        }
    )

    val trackManager = AudioTrackManager(
        onAmplitudeChanged = { outRms ->
            currentOutputRms = outRms
            onAmplitudeChanged(currentInputRms, outRms)
        },
        onSpeakingStateChanged = onSpeakingStateChanged
    )

    /**
     * Starts low-latency 16kHz AudioRecord capture.
     */
    fun startRecording(): Boolean {
        Log.d(TAG, "Starting 16kHz AudioRecord capture")
        return recorder.start()
    }

    /**
     * Stops AudioRecord capture.
     */
    fun stopRecording() {
        Log.d(TAG, "Stopping 16kHz AudioRecord capture")
        recorder.stop()
        currentInputRms = 0f
        onAmplitudeChanged(0f, currentOutputRms)
    }

    /**
     * Starts low-latency 16kHz AudioTrack playback worker.
     */
    fun startPlayback() {
        Log.d(TAG, "Starting 16kHz AudioTrack playback worker")
        trackManager.start()
    }

    /**
     * Enqueues incoming PCM audio chunks (resampling from source rate to 16kHz if needed).
     */
    fun enqueueAudio(pcmData: ByteArray, sourceSampleRate: Int = SAMPLE_RATE_HZ) {
        trackManager.enqueueAudio(pcmData, sourceSampleRate)
    }

    /**
     * Instant Barge-in: clears playback queue and flushes hardware AudioTrack buffer immediately.
     */
    fun bargeIn() {
        Log.d(TAG, "Executing instant barge-in")
        trackManager.bargeIn()
        currentOutputRms = 0f
        onAmplitudeChanged(currentInputRms, 0f)
    }

    /**
     * Stops AudioTrack playback.
     */
    fun stopPlayback() {
        Log.d(TAG, "Stopping 16kHz AudioTrack playback")
        trackManager.stop()
        currentOutputRms = 0f
        onAmplitudeChanged(currentInputRms, 0f)
    }

    /**
     * Releases all audio recording and playback hardware resources.
     */
    fun release() {
        Log.d(TAG, "Releasing AudioManager hardware resources")
        recorder.release()
        trackManager.release()
        currentInputRms = 0f
        currentOutputRms = 0f
        onAmplitudeChanged(0f, 0f)
    }
}
