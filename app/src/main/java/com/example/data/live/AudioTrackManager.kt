package com.example.data.live

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

/**
 * Robust, zero-latency AudioTrack manager running at 16kHz.
 * - Configured with 16kHz 16-bit Mono linear PCM.
 * - Thread-safe buffer queue with atomic generation counter to prevent overlapping audio.
 * - Zero-polling-latency playback loop: uses monitor wait/notify for instant response.
 * - Instant barge-in: atomic flush of hardware track buffer and queue purge.
 * - High-speed linear resampler for non-16kHz audio sources (e.g. Gemini 24kHz -> 16kHz).
 * - Priority: THREAD_PRIORITY_URGENT_AUDIO.
 */
class AudioTrackManager(
    private val onAmplitudeChanged: (Float) -> Unit,
    private val onSpeakingStateChanged: (Boolean) -> Unit
) {
    companion object {
        private const val TAG = "AudioTrackManager"
        const val SAMPLE_RATE_HZ = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null

    private val isRunning = AtomicBoolean(false)
    private val isInterrupted = AtomicBoolean(false)
    private val generationCounter = AtomicInteger(0)

    // Thread-safe audio chunk container with generation tag
    private data class QueuedAudioChunk(
        val data: ByteArray,
        val generation: Int
    )

    private val bufferQueue = ConcurrentLinkedQueue<QueuedAudioChunk>()
    private val queueLock = Object()
    private val trackLock = Any()

    /**
     * Initializes and starts the 16kHz AudioTrack playback worker.
     */
    @Synchronized
    fun start() {
        if (isRunning.get()) return

        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE_HZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        // Double-buffering minimum required size or 8192 bytes for glitch-free low latency
        val bufferSize = maxOf(minBufferSize * 2, 8192)

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE_HZ)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            isRunning.set(true)
            isInterrupted.set(false)

            playbackThread = Thread({
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
                var wasSpeaking = false

                while (isRunning.get()) {
                    // Check if interrupted
                    if (isInterrupted.get()) {
                        bufferQueue.clear()
                        isInterrupted.set(false)
                        if (wasSpeaking) {
                            wasSpeaking = false
                            onAmplitudeChanged(0f)
                            onSpeakingStateChanged(false)
                        }
                        continue
                    }

                    val chunk = bufferQueue.poll()
                    val currentGen = generationCounter.get()

                    if (chunk != null) {
                        // Discard chunks from a superseded generation (barge-in happened)
                        if (chunk.generation != currentGen) {
                            continue
                        }

                        if (!wasSpeaking) {
                            wasSpeaking = true
                            onSpeakingStateChanged(true)
                        }

                        val rms = calculateRms(chunk.data)
                        onAmplitudeChanged(rms)

                        synchronized(trackLock) {
                            val track = audioTrack
                            if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING && !isInterrupted.get()) {
                                track.write(chunk.data, 0, chunk.data.size, AudioTrack.WRITE_BLOCKING)
                            }
                        }
                    } else {
                        if (wasSpeaking) {
                            wasSpeaking = false
                            onAmplitudeChanged(0f)
                            onSpeakingStateChanged(false)
                        }

                        // Wait immediately for new audio without busy-spinning or fixed sleep
                        synchronized(queueLock) {
                            if (bufferQueue.isEmpty() && isRunning.get() && !isInterrupted.get()) {
                                try {
                                    queueLock.wait(100) // wake up on notify or safety timeout
                                } catch (e: InterruptedException) {
                                    // Worker was interrupted
                                }
                            }
                        }
                    }
                }
            }, "AudioTrackManager-Playback").apply { start() }

            Log.d(TAG, "AudioTrackManager started successfully at 16kHz")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioTrackManager: ${e.message}", e)
        }
    }

    /**
     * Enqueues audio data for playback.
     * Automatically handles resampling if the source audio is not 16kHz (e.g., 24kHz from Gemini Live).
     * Rejects data if a barge-in event has superseded this turn.
     */
    fun enqueueAudio(pcmData: ByteArray, sourceSampleRate: Int = SAMPLE_RATE_HZ) {
        if (!isRunning.get()) {
            start()
        }
        if (isInterrupted.get() || pcmData.isEmpty()) {
            return
        }

        val currentGen = generationCounter.get()

        // Resample to 16kHz if needed
        val targetPcm = if (sourceSampleRate != SAMPLE_RATE_HZ) {
            resampleLinear(pcmData, sourceSampleRate, SAMPLE_RATE_HZ)
        } else {
            pcmData
        }

        // Verify generation hasn't changed during resampling
        if (generationCounter.get() == currentGen) {
            bufferQueue.add(QueuedAudioChunk(targetPcm, currentGen))
            synchronized(queueLock) {
                queueLock.notify() // Immediate zero-latency wakeup
            }
        }
    }

    /**
     * Instant Barge-In implementation:
     * 1. Increments generation counter to reject any stale in-flight packets.
     * 2. Clears the thread-safe buffer queue.
     * 3. Flushes the hardware AudioTrack buffer immediately to cut off playing sound.
     * 4. Resets speaking status and amplitude.
     */
    fun bargeIn() {
        generationCounter.incrementAndGet()
        isInterrupted.set(true)
        bufferQueue.clear()

        synchronized(trackLock) {
            try {
                audioTrack?.let { track ->
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.pause()
                        track.flush()
                        track.play()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error flushing AudioTrack on barge-in: ${e.message}")
            }
        }

        synchronized(queueLock) {
            queueLock.notifyAll()
        }

        onAmplitudeChanged(0f)
        onSpeakingStateChanged(false)
        Log.d(TAG, "Barge-in executed: audio queue purged and hardware track flushed")
    }

    /**
     * Stops the playback engine and releases resources.
     */
    @Synchronized
    fun stop() {
        if (!isRunning.getAndSet(false)) return

        bargeIn()

        try {
            playbackThread?.interrupt()
            playbackThread = null

            synchronized(trackLock) {
                audioTrack?.apply {
                    if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                        stop()
                    }
                    release()
                }
                audioTrack = null
            }
            onAmplitudeChanged(0f)
            onSpeakingStateChanged(false)
            Log.d(TAG, "AudioTrackManager stopped and released")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioTrackManager: ${e.message}")
        }
    }

    fun release() {
        stop()
    }

    /**
     * High-performance linear resampler from inRate to outRate for 16-bit mono PCM.
     */
    private fun resampleLinear(pcmData: ByteArray, inRate: Int, outRate: Int): ByteArray {
        if (inRate == outRate || pcmData.isEmpty()) return pcmData
        val inSampleCount = pcmData.size / 2
        val outSampleCount = ((inSampleCount.toLong() * outRate) / inRate).toInt()
        val outputBytes = ByteArray(outSampleCount * 2)
        val ratio = inRate.toDouble() / outRate.toDouble()

        for (j in 0 until outSampleCount) {
            val pos = j * ratio
            val idx = pos.toInt()
            val frac = (pos - idx).toFloat()

            val s0 = ((pcmData[idx * 2 + 1].toInt() shl 8) or (pcmData[idx * 2].toInt() and 0xFF)).toShort()
            val s1 = if (idx + 1 < inSampleCount) {
                ((pcmData[(idx + 1) * 2 + 1].toInt() shl 8) or (pcmData[(idx + 1) * 2].toInt() and 0xFF)).toShort()
            } else {
                s0
            }

            val interpolated = (s0 * (1f - frac) + s1 * frac).toInt().coerceIn(-32768, 32767)
            outputBytes[j * 2] = (interpolated and 0xFF).toByte()
            outputBytes[j * 2 + 1] = ((interpolated shr 8) and 0xFF).toByte()
        }
        return outputBytes
    }

    /**
     * Calculates normalized RMS amplitude for 16-bit PCM little-endian buffer.
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
