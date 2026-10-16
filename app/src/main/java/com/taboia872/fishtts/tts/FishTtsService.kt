package com.taboia872.fishtts.tts

import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import com.taboia872.fishtts.fish.FishApi
import com.taboia872.fishtts.settings.AppSettings
import java.util.Locale

/**
 * The system TTS engine. Any app that asks Android to speak goes through
 * here: we synthesize via FishAudio (format "pcm", 16-bit mono 24 kHz)
 * and stream the bytes into the framework's SynthesisCallback.
 *
 * Language model: FishAudio is multilingual — one voice, any language —
 * so we report support for every locale (LANG_AVAILABLE) and expose ONE
 * voice entry ("fish-audio") plus one per FAVORITE, so the user can pick
 * a favorite directly from the system voice list too.
 *
 * STOP CONTRACT (framework): onStop() is called on the main thread while
 * onSynthesizeText blocks on the synthesis thread. We honor it with a
 * volatile flag polled during the network call and the PCM push loop.
 */
class FishTtsService : TextToSpeechService() {

    companion object {
        private const val TAG = "FishTtsService"
        private const val SAMPLE_RATE = 24_000
        const val VOICE_DEFAULT = "fish-audio"
        private const val VOICE_PREFIX = "fish-"
    }

    @Volatile
    private var stopped = false

    // ------------------------------------------------------ language API

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        // FishAudio models are multilingual: accept every locale.
        return TextToSpeech.LANG_AVAILABLE
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int =
        TextToSpeech.LANG_AVAILABLE

    override fun onGetLanguage(): Array<String> = arrayOf("por", "BRA", "")

    // ------------------------------------------------------ voices (API 21+)

    override fun onGetVoices(): MutableList<Voice> {
        val out = mutableListOf<Voice>()
        val cfg = AppSettings.config.value
        val features = setOf("networkSynthesis")
        out.add(
            Voice(
                VOICE_DEFAULT,
                Locale.forLanguageTag("pt-BR"),
                Voice.QUALITY_NORMAL,
                Voice.LATENCY_NORMAL,
                true,
                features
            )
        )
        // Favorites surface as pickable voices in the system settings.
        AppSettings.favoriteList().forEach { (id, title) ->
            out.add(
                Voice(
                    VOICE_PREFIX + id,
                    Locale.forLanguageTag("pt-BR"),
                    Voice.QUALITY_NORMAL,
                    Voice.LATENCY_NORMAL,
                    true,
                    features
                )
            )
        }
        return out
    }

    override fun onIsValidVoiceName(voiceName: String?): Int {
        if (voiceName == null) return TextToSpeech.ERROR
        if (voiceName == VOICE_DEFAULT) return TextToSpeech.SUCCESS
        if (voiceName.startsWith(VOICE_PREFIX)) {
            val id = voiceName.removePrefix(VOICE_PREFIX)
            if (AppSettings.favoriteList().any { it.first == id }) {
                return TextToSpeech.SUCCESS
            }
        }
        return TextToSpeech.ERROR
    }

    override fun onLoadVoice(voiceName: String?): Int =
        if (onIsValidVoiceName(voiceName) == TextToSpeech.SUCCESS)
            TextToSpeech.SUCCESS else TextToSpeech.ERROR

    override fun onGetDefaultVoiceNameFor(
        lang: String?,
        country: String?,
        variant: String?
    ): String? = VOICE_DEFAULT

    // ------------------------------------------------------ synthesis

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (request == null || callback == null) return
        stopped = false
        val cfg = AppSettings.config.value
        if (cfg.apiKey.isBlank()) {
            Log.e(TAG, "No API key configured")
            callback.error()
            return
        }

        // Requested voice: "fish-<voiceId>" (a favorite) or the default,
        // which resolves to the voice selected in the app.
        var voiceId = cfg.fishVoiceId
        request.voiceName?.takeIf { it.isNotBlank() }?.let { name ->
            voiceId = when {
                name.startsWith(VOICE_PREFIX) && name != VOICE_DEFAULT ->
                    name.removePrefix(VOICE_PREFIX)
                else -> cfg.fishVoiceId
            }
        }

        val text = (request.charSequenceText ?: request.text ?: "").toString()
        if (text.isBlank()) {
            callback.error()
            return
        }

        val pcm = try {
            FishApi.synthesizePcm(
                apiKey = cfg.apiKey,
                text = text,
                model = cfg.fishModel,
                voiceId = voiceId,
                sampleRate = SAMPLE_RATE,
                stopFlag = { stopped }
            )
        } catch (e: FishApi.StopRequested) {
            Log.i(TAG, "Synthesis stopped by client")
            return
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed: " + e.message)
            callback.error()
            return
        }

        // Deliver PCM to the framework (playback / file sinks).
        callback.start(SAMPLE_RATE, android.media.AudioFormat.ENCODING_PCM_16BIT, 1)
        val chunk = 8192
        var off = 0
        while (off < pcm.size) {
            if (stopped) return
            val len = minOf(chunk, pcm.size - off)
            callback.audioAvailable(pcm, off, len)
            off += len
        }
        callback.done()
    }

    override fun onStop() {
        stopped = true
    }
}
