package com.example.data.live

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Robust, zero-latency AudioRecorder using the Android AudioRecord class at 16kHz.
 * - Captures 16kHz 16-bit mono linear PCM audio.
 * - Low-latency chunk buffer (40ms / 640 samples = 1280 bytes) for real-time WebSocket streaming.
 * - Hardware AcousticEchoCanceler and NoiseSuppressor integration.
 * - Preallocated buffer to avoid runtime garbage collection stalls.
 * - Runs with THREAD_PRIORITY_URGENT_AUDIO.
 */
class AudioRecorder(
    private val context: Context,
    private val onAudioChunkRecorded: (ByteArray) -> Unit,
    private val onAmplitudeChanged: (Float) -> Unit
) {
    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE_HZ = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // 40ms frame at 16kHz mono 16-bit: 640 samples = 1280 bytes
        // Ensures sub-frame real-time streaming with zero perceptible input lag
        const val CHUNK_SAMPLES = 640
        const val CHUNK_BYTES = CHUNK_SAMPLES * 2
    }

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var captureThread: Thread? = null
    private val isRecording = AtomicBoolean(false)

    /**
     * Initializes and starts low-latency 16kHz audio capture.
     */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(): Boolean {
        if (isRecording.get()) return true

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        // Double-buffering minimum required size or 4 frames to avoid overflow
        val bufferSize = maxOf(minBufferSize * 2, CHUNK_BYTES * 4)

        try {
            val sources = listOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC
            )
            for (source in sources) {
                try {
                    val record = AudioRecord(source, SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        audioRecord = record
                        Log.d(TAG, "AudioRecord initialized with audio source $source")
                        break
                    } else {
                        record.release()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed source $source: ${e.message}")
                }
            }

            if (audioRecord == null || audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed on all sources")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            val sessionId = audioRecord?.audioSessionId ?: 0
            if (sessionId != 0) {
                if (AcousticEchoCanceler.isAvailable()) {
                    try {
                        echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
                        Log.d(TAG, "AcousticEchoCanceler enabled successfully on session $sessionId")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to enable AcousticEchoCanceler: ${e.message}")
                    }
                }

                if (NoiseSuppressor.isAvailable()) {
                    try {
                        noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
                        Log.d(TAG, "NoiseSuppressor enabled successfully on session $sessionId")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to enable NoiseSuppressor: ${e.message}")
                    }
                }
            }

            audioRecord?.startRecording()
            isRecording.set(true)

            captureThread = Thread({
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
                val readBuffer = ByteArray(CHUNK_BYTES)

                while (isRecording.get()) {
                    val bytesRead = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: -1
                    if (bytesRead > 0) {
                        val chunkCopy = readBuffer.copyOf(bytesRead)
                        val rms = calculateRms(chunkCopy)
                        onAmplitudeChanged(rms)
                        onAudioChunkRecorded(chunkCopy)
                    } else if (bytesRead < 0) {
                        Log.w(TAG, "AudioRecord read returned error code: $bytesRead")
                    }
                }
            }, "AudioRecorder-Capture").apply { start() }

            Log.d(TAG, "AudioRecorder started successfully at 16kHz")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting AudioRecorder: ${e.message}", e)
            stop()
            return false
        }
    }

    /**
     * Stops audio capture and frees hardware resources.
     */
    @Synchronized
    fun stop() {
        if (!isRecording.getAndSet(false)) return

        try {
            captureThread?.interrupt()
            captureThread = null

            echoCanceler?.release()
            echoCanceler = null

            noiseSuppressor?.release()
            noiseSuppressor = null

            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
            audioRecord = null
            onAmplitudeChanged(0f)
            Log.d(TAG, "AudioRecorder stopped and released")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecorder: ${e.message}")
        }
    }

    fun isRecording(): Boolean = isRecording.get()

    fun release() {
        stop()
    }

    /**
     * Calculates normalized RMS amplitude (0.0f .. 1.0f) for 16-bit PCM little-endian buffer.
     */
    private fun calculateRms(pcmBytes: ByteArray): Float {
        if (pcmBytes.isEmpty()) return 0f
        val sampleCount = pcmBytes.size / 2
        if (sampleCount == 0) return 0f

        var sumSquares = 0.0
        var i = 0
        while (i < pcmBytes.size - 1) {
            val sample = ((pcmBytes[i + 1].toInt() shl 8) or (pcmBytes[i].toInt() and 0xFF)).toShort()
            val normalized = sample / 32768.0
            sumSquares += normalized * normalized
            i += 2
        }

        val rms = sqrt(sumSquares / sampleCount).toFloat()
        return (rms * 2.8f).coerceIn(0f, 1f)
    }
}
