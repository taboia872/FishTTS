package com.taboia872.fishtts.fish

import com.taboia872.fishtts.net.Net
import com.taboia872.fishtts.settings.AppSettings
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URLEncoder


/**
 * FishAudio online TTS client (https://api.fish.audio/v1/tts).
 *
 * API SHAPE GOTCHA (confirmed in current docs): the MODEL goes in the
 * request HEADER ("model: s2.1-pro-free"), NOT in the JSON body. The body
 * carries text, reference_id (voice), format and sampling params.
 *
 * Voice catalog: GET https://api.fish.audio/model — server-side search
 * via title= param, pagination via page_number (has_more flag).
 */
object FishApi {

    data class FishVoice(
        val id: String,
        val title: String,
        val titleNormalized: String = "",
        /** ISO language codes, e.g. ["en"], ["pt"] (live-verified shape). */
        val languages: List<String> = emptyList()
    ) {
        /** Case/diacritic-insensitive match key for client-side filtering. */
        fun matches(q: String): Boolean {
            val n = if (titleNormalized.isNotBlank()) titleNormalized else normalize(title)
            return n.contains(normalize(q))
        }
    }

    fun normalize(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .trim()

    private const val TTS_ENDPOINT = "https://api.fish.audio/v1/tts"
    private const val VOICES_URL = "https://api.fish.audio/model"

    /** Publicly known TTS model labels for the model picker. */
    val MODELS = listOf(
        FishModel("s2.1-pro-free", "Free developer tier", free = true),
        FishModel("s2.1-pro", "Production quality", free = false),
        FishModel("s2-pro", "Previous generation", free = false),
        FishModel("speech-1.5", "Legacy", free = false)
    )

    data class FishModel(val id: String, val label: String, val free: Boolean)

    data class VoicePage(val voices: List<FishVoice>, val hasMore: Boolean)

    /**
     * List voices from FishAudio's public catalog, page by page.
     * Server-side search via title=; local filter is ALSO applied so
     * diacritic-insensitive matching works (server search is exact-ish).
     */
    fun listVoices(
        apiKey: String,
        search: String = "",
        pageNumber: Int = 1,
        pageSize: Int = 50,
        language: String = "",
        sortBy: String = ""
    ): Result<VoicePage> {
        val key = apiKey
        if (key.isBlank()) {
            return Result.failure(IllegalStateException("No FishAudio API key"))
        }
        return runCatching {
            val searchQ = if (search.isNotBlank())
                "&title=" + URLEncoder.encode(search, "UTF-8") else ""
            val langQ = if (language.isNotBlank()) "&language=$language" else ""
            val sortQ = if (sortBy.isNotBlank()) "&sort_by=$sortBy" else ""
            val url = "$VOICES_URL?page_size=$pageSize&page_number=$pageNumber$searchQ$langQ$sortQ"
            val conn = Net.open(url).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (conn.responseCode != 200) {
                    throw RuntimeException("HTTP ${conn.responseCode}")
                }
                val root = JSONObject(conn.inputStream.bufferedReader().readText())
                val items: JSONArray = root.optJSONArray("items") ?: JSONArray()
                val out = ArrayList<FishVoice>(items.length())
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val id = item.optString("_id", "")
                    if (id.isEmpty()) continue
                    val title = item.optString("title", "").ifEmpty { "Voice $id" }
                    val langs = ArrayList<String>()
                    val langArr = item.optJSONArray("languages")
                    if (langArr != null) {
                        for (j in 0 until langArr.length()) {
                            langArr.optJSONObject(j)?.let { o ->
                                val code = o.optString("language")
                                if (code.isNotBlank()) langs.add(code)
                            } ?: run {
                                val s = langArr.optString(j)
                                if (s.isNotBlank()) langs.add(s)
                            }
                        }
                    }
                    out.add(FishVoice(id, title, normalize(title), langs))
                }
                VoicePage(out, root.optBoolean("has_more", false))
            } finally {
                conn.disconnect()
            }
        }
    }

    /**
     * Synthesize text to 16-bit mono PCM at the given sample rate
     * (FishAudio "pcm" format). Returns raw PCM bytes.
     *
     * [stopFlag] is polled while writing the request body and reading the
     * response: when it turns true the call aborts (throws StopRequested).
     */
    class StopRequested : Exception("stop")

    fun synthesizePcm(
        apiKey: String,
        text: String,
        model: String,
        voiceId: String,
        sampleRate: Int,
        chunkLength: Int = 300,
        stopFlag: () -> Boolean
    ): ByteArray {
        val body = JSONObject().apply {
            put("text", text)
            put("format", "pcm")
            put("sample_rate", sampleRate)
            put("chunk_length", chunkLength)
            put("normalize", true)
            put("latency", "balanced")
            if (voiceId.isNotBlank()) put("reference_id", voiceId)
        }

        val conn = Net.open(TTS_ENDPOINT).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            // MODEL IN HEADER — FishAudio contract
            setRequestProperty("model", model.ifBlank { "s2.1-pro-free" })
        }

        try {
            val payload = body.toString().toByteArray(Charsets.UTF_8)
            conn.outputStream.use { it.write(payload) }
            if (stopFlag()) throw StopRequested()

            val status = conn.responseCode
            if (status != 200) {
                val err = conn.errorStream?.bufferedReader()?.readText()?.take(300) ?: ""
                val msg = JSONObject().let { jo ->
                    try {
                        jo.put("tmp", JSONObject(err).optString("message", ""))
                        jo.optString("tmp")
                    } catch (e: Exception) { "" }
                }
                throw RuntimeException(
                    "HTTP $status" + if (msg.isNotBlank()) ": $msg" else ": $err"
                )
            }

            val buf = ByteArrayOutputStream(1 shl 20)
            val chunk = ByteArray(8192)
            conn.inputStream.use { input ->
                while (true) {
                    if (stopFlag()) throw StopRequested()
                    val n = input.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                }
            }
            return buf.toByteArray()
        } finally {
            conn.disconnect()
        }
    }
}
