package com.opus.music.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Stores server credentials in plain SharedPreferences.
 * (Encrypted prefs via Keystore/Tink caused startup crashes on some devices;
 *  plain prefs are stable. Credentials are only a music-server login.)
 */
class SettingsRepository(private val context: Context) {
    private val _config = MutableStateFlow<ServerConfig?>(null)
    val configFlow: Flow<ServerConfig?> = _config

    private val prefs: SharedPreferences by lazy {
        val p = context.getSharedPreferences("opus_prefs", Context.MODE_PRIVATE)
        _config.value = load(p)
        p
    }

    /** Safe to call from any thread; never throws. */
    fun ensureInit() {
        try {
            prefs // trigger lazy init
        } catch (_: Exception) {}
    }

    private fun load(p: SharedPreferences): ServerConfig? {
        return try {
            val url = p.getString(KEY_URL, "").orEmpty().trim()
            val user = p.getString(KEY_USER, "").orEmpty().trim()
            val pass = p.getString(KEY_PASS, "").orEmpty()
            if (url.isBlank() || user.isBlank() || pass.isEmpty()) null
            else ServerConfig(url, user, pass)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun save(url: String, username: String, password: String) {
        try {
            // Use commit() (synchronous) not apply(): if the app crashes seconds
            // after login, apply()'s queued disk write may never complete and
            // the user has to re-enter credentials.
            prefs.edit()
                .putString(KEY_URL, url.trim())
                .putString(KEY_USER, username.trim())
                .putString(KEY_PASS, password)
                .commit()
        } catch (_: Exception) {}
        _config.value = ServerConfig(url.trim(), username.trim(), password)
    }

    suspend fun clear() {
        try {
            prefs.edit().clear().apply()
        } catch (_: Exception) {}
        _config.value = null
    }

    companion object {
        private const val KEY_URL = "server_url"
        private const val KEY_USER = "username"
        private const val KEY_PASS = "password"
        private const val KEY_BITRATE = "stream_bitrate" // 0 = original, else kbps
        private const val KEY_SCROBBLE = "scrobble_enabled"
        private const val KEY_SCREEN_LOCK = "prevent_screen_lock" // never | playing | always
        private const val KEY_GAPLESS = "gapless_enabled"
        private const val KEY_ARTWORK_HIGH = "artwork_high_quality"
        private const val KEY_CROSSFADE = "crossfade_sec" // 0 = off
        private const val KEY_SLEEP_FADE = "sleep_fade_min"
        private const val KEY_SLEEP_EOT = "sleep_end_of_track"
        private const val KEY_MIX_ENABLED = "offline_mix_enabled"
        private const val KEY_MIX_SIZE = "offline_mix_size"
        private const val KEY_MIX_WIFI = "offline_mix_wifi_only"
    }

    // --- App preferences (not credentials) ---

    fun getStreamBitrate(): Int = try {
        prefs.getInt(KEY_BITRATE, 0)
    } catch (_: Exception) { 0 }

    fun setStreamBitrate(kbps: Int) {
        try {
            prefs.edit().putInt(KEY_BITRATE, kbps).apply()
        } catch (_: Exception) {}
    }

    fun isScrobbleEnabled(): Boolean = try {
        prefs.getBoolean(KEY_SCROBBLE, true)
    } catch (_: Exception) { true }

    fun setScrobbleEnabled(enabled: Boolean) {
        try {
            prefs.edit().putBoolean(KEY_SCROBBLE, enabled).apply()
        } catch (_: Exception) {}
    }

    // --- Amperfy-style settings ---

    /** never | playing | always */
    fun getPreventScreenLock(): String = try {
        prefs.getString(KEY_SCREEN_LOCK, "never") ?: "never"
    } catch (_: Exception) { "never" }

    fun setPreventScreenLock(value: String) {
        try {
            prefs.edit().putString(KEY_SCREEN_LOCK, value).apply()
        } catch (_: Exception) {}
    }

    fun isGaplessEnabled(): Boolean = try {
        prefs.getBoolean(KEY_GAPLESS, true)
    } catch (_: Exception) { true }

    fun setGaplessEnabled(enabled: Boolean) {
        try {
            prefs.edit().putBoolean(KEY_GAPLESS, enabled).apply()
        } catch (_: Exception) {}
    }

    fun isArtworkHighQuality(): Boolean = try {
        prefs.getBoolean(KEY_ARTWORK_HIGH, true)
    } catch (_: Exception) { true }

    fun setArtworkHighQuality(enabled: Boolean) {
        try {
            prefs.edit().putBoolean(KEY_ARTWORK_HIGH, enabled).apply()
        } catch (_: Exception) {}
    }

    // --- Crossfade ---

    /** Overlap between tracks in seconds; 0 = off. */
    fun getCrossfadeSec(): Int = try {
        prefs.getInt(KEY_CROSSFADE, 0)
    } catch (_: Exception) { 0 }

    fun setCrossfadeSec(sec: Int) {
        try {
            prefs.edit().putInt(KEY_CROSSFADE, sec.coerceIn(0, 12)).apply()
        } catch (_: Exception) {}
    }

    // --- Smart sleep fade ---

    /** Minutes over which the volume fades out before the timer ends. */
    fun getSleepFadeMin(): Int = try {
        prefs.getInt(KEY_SLEEP_FADE, 5)
    } catch (_: Exception) { 5 }

    fun setSleepFadeMin(min: Int) {
        try {
            prefs.edit().putInt(KEY_SLEEP_FADE, min.coerceIn(1, 15)).apply()
        } catch (_: Exception) {}
    }

    fun isSleepEndOfTrack(): Boolean = try {
        prefs.getBoolean(KEY_SLEEP_EOT, false)
    } catch (_: Exception) { false }

    fun setSleepEndOfTrack(enabled: Boolean) {
        try {
            prefs.edit().putBoolean(KEY_SLEEP_EOT, enabled).apply()
        } catch (_: Exception) {}
    }

    // --- Smart Offline Mix ---

    fun isOfflineMixEnabled(): Boolean = try {
        prefs.getBoolean(KEY_MIX_ENABLED, false)
    } catch (_: Exception) { false }

    fun setOfflineMixEnabled(enabled: Boolean) {
        try {
            prefs.edit().putBoolean(KEY_MIX_ENABLED, enabled).apply()
        } catch (_: Exception) {}
    }

    fun getOfflineMixSize(): Int = try {
        prefs.getInt(KEY_MIX_SIZE, 50)
    } catch (_: Exception) { 50 }

    fun setOfflineMixSize(n: Int) {
        try {
            prefs.edit().putInt(KEY_MIX_SIZE, n.coerceIn(10, 200)).apply()
        } catch (_: Exception) {}
    }

    fun isOfflineMixWifiOnly(): Boolean = try {
        prefs.getBoolean(KEY_MIX_WIFI, true)
    } catch (_: Exception) { true }

    fun setOfflineMixWifiOnly(enabled: Boolean) {
        try {
            prefs.edit().putBoolean(KEY_MIX_WIFI, enabled).apply()
        } catch (_: Exception) {}
    }
}
