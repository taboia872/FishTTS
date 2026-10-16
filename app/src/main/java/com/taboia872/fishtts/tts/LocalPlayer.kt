package com.taboia872.fishtts.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Plays synthesized PCM locally (test screen). The system-engine path
 * (FishTtsService) streams into SynthesisCallback instead; this player
 * exists only so the user can audition voices before selecting one.
 */
object LocalPlayer {

    private const val SAMPLE_RATE = 24_000

    sealed class State {
        data object Idle : State()
        data object Synthesizing : State()
        data class Playing(val totalMs: Long) : State()
        data class Failed(val reason: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var currentJob: Thread? = null

    fun stop() {
        currentJob?.interrupt()
        currentJob = null
        _state.value = State.Idle
    }

    /** Blocking synthesis + playback, run on a worker thread. */
    suspend fun speak(
        apiKey: String,
        text: String,
        model: String,
        voiceId: String
    ) = withContext(Dispatchers.IO) {
        _state.value = State.Synthesizing
        val me = Thread.currentThread()
        currentJob = me
        try {
            val pcm = com.taboia872.fishtts.fish.FishApi.synthesizePcm(
                apiKey = apiKey,
                text = text,
                model = model,
                voiceId = voiceId,
                sampleRate = SAMPLE_RATE,
                stopFlag = { Thread.currentThread().isInterrupted }
            )
            playPcm(pcm)
        } catch (e: com.taboia872.fishtts.fish.FishApi.StopRequested) {
            _state.value = State.Idle
        } catch (e: Exception) {
            _state.value = State.Failed(e.message ?: "error")
        }
    }

    private fun playPcm(pcm: ByteArray) {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuf, 8192))
            .build()
        try {
            track.play()
            var off = 0
            while (off < pcm.size) {
                if (Thread.currentThread().isInterrupted) return
                val len = minOf(8192, pcm.size - off)
                track.write(pcm, off, len)
                off += len
            }
            // Wait until everything buffered was actually played out.
            while (!Thread.currentThread().isInterrupted) {
                val head = track.playbackHeadPosition
                if (head * 2 >= pcm.size) break
                Thread.sleep(20)
            }
        } finally {
            _state.value = State.Idle
            runCatching { track.stop() }
            runCatching { track.release() }
            currentJob = null
        }
    }
}
