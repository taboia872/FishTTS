package com.taboia872.fishtts.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * The single source of truth for app configuration. The UI and the TTS
 * service both read the same StateFlow; every write goes through update().
 * The TTS engine process reads the same encrypted prefs (init is called
 * from App.onCreate, which runs in the service process too).
 */
data class AppConfig(
    /** FishAudio API key */
    val apiKey: String = "",
    /** FishAudio TTS model — goes in the request HEADER, not body */
    val fishModel: String = "s2.1-pro-free",
    /** Selected FishAudio voice id (reference_id); blank = platform default */
    val fishVoiceId: String = "",
    /** Display name of the selected voice */
    val fishVoiceName: String = "",
    /** Favorite voices: JSON array of {id,title} */
    val fishFavorites: String = "[]"
)

object AppSettings {

    private const val FILE = "fishtts_secure_prefs"
    private const val K_API_KEY = "api_key"
    private const val K_FISH_MODEL = "fish_model"
    private const val K_FISH_VOICE_ID = "fish_voice_id"
    private const val K_FISH_VOICE_NAME = "fish_voice_name"
    private const val K_FISH_FAVORITES = "fish_favorites"

    private var prefs: SharedPreferences? = null

    private val _config = MutableStateFlow(AppConfig())
    val config: StateFlow<AppConfig> = _config

    /** Call once from Application/Activity onCreate. Thread-safe. */
    fun init(context: Context) {
        synchronized(this) {
            if (prefs == null) {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                prefs = EncryptedSharedPreferences.create(
                    context.applicationContext, FILE, masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                _config.value = load()
            }
        }
    }

    private fun p(): SharedPreferences =
        prefs ?: throw IllegalStateException("AppSettings.init(context) was not called")

    private fun load(): AppConfig {
        val p = p()
        return AppConfig(
            apiKey = p.getString(K_API_KEY, "") ?: "",
            fishModel = p.getString(K_FISH_MODEL, "s2.1-pro-free") ?: "s2.1-pro-free",
            fishVoiceId = p.getString(K_FISH_VOICE_ID, "") ?: "",
            fishVoiceName = p.getString(K_FISH_VOICE_NAME, "") ?: "",
            fishFavorites = p.getString(K_FISH_FAVORITES, "[]") ?: "[]"
        )
    }

    /** Atomically change config: persist + emit to all collectors. */
    fun update(transform: (AppConfig) -> AppConfig) {
        val next = transform(_config.value)
        p().edit()
            .putString(K_API_KEY, next.apiKey)
            .putString(K_FISH_MODEL, next.fishModel)
            .putString(K_FISH_VOICE_ID, next.fishVoiceId)
            .putString(K_FISH_VOICE_NAME, next.fishVoiceName)
            .putString(K_FISH_FAVORITES, next.fishFavorites)
            .apply()
        _config.value = next
    }

    // ------------------------------------------------------- favorites

    /** Favorite voices as (id, title) pairs. */
    fun favoriteList(): List<Pair<String, String>> {
        return try {
            val arr = JSONArray(_config.value.fishFavorites)
            val out = mutableListOf<Pair<String, String>>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isNotBlank()) out.add(id to o.optString("title").ifBlank { id })
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun toggleFavorite(id: String, title: String) {
        val cur = favoriteList().toMutableList()
        val existing = cur.indexOfFirst { it.first == id }
        if (existing >= 0) cur.removeAt(existing) else cur.add(id to title)
        val arr = JSONArray()
        cur.forEach { arr.put(JSONObject().put("id", it.first).put("title", it.second)) }
        update { it.copy(fishFavorites = arr.toString()) }
    }

    fun isFavorite(id: String): Boolean =
        favoriteList().any { it.first == id }
}
