package com.opus.music.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Snapshot of a song's metadata, stored alongside its play count so the
 *  offline mix can be built without extra server round-trips. */
@Serializable
data class SongMeta(
    val id: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val coverArt: String? = null,
    val duration: Int = 0,
    val suffix: String? = null
)

/** A mix candidate with its score inputs. */
data class MixCandidate(
    val meta: SongMeta,
    val playCount: Int,
    val starred: Boolean
)

/**
 * Local listening stats: per-song play counts plus metadata snapshots.
 * Backs Smart Offline Mix ("most played + favorites, auto-downloaded").
 */
class StatsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("opus_stats", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private fun countKey(id: String) = "pc_$id"
    private fun metaKey(id: String) = "meta_$id"

    /** Record one play of a song (call when a track has been listened to). */
    fun recordPlay(meta: SongMeta) {
        try {
            val n = prefs.getInt(countKey(meta.id), 0) + 1
            prefs.edit()
                .putInt(countKey(meta.id), n)
                .putString(metaKey(meta.id), json.encodeToString(SongMeta.serializer(), meta))
                .apply()
        } catch (_: Exception) { }
    }

    fun playCount(id: String): Int = try {
        prefs.getInt(countKey(id), 0)
    } catch (_: Exception) { 0 }

    /** Top played songs, most-played first. */
    fun topPlayed(limit: Int): List<MixCandidate> = try {
        prefs.all.keys
            .filter { it.startsWith("pc_") }
            .mapNotNull { k ->
                val id = k.removePrefix("pc_")
                val n = (prefs.all[k] as? Int) ?: 0
                val metaStr = prefs.getString(metaKey(id), null) ?: return@mapNotNull null
                val meta = try {
                    json.decodeFromString(SongMeta.serializer(), metaStr)
                } catch (_: Exception) { return@mapNotNull null }
                MixCandidate(meta, n, starred = false)
            }
            .sortedByDescending { it.playCount }
            .take(limit.coerceAtLeast(1))
    } catch (_: Exception) { emptyList() }

    fun getLastMixSync(): Long = try {
        prefs.getLong("last_mix_sync", 0L)
    } catch (_: Exception) { 0L }

    fun setLastMixSync(ts: Long) {
        try { prefs.edit().putLong("last_mix_sync", ts).apply() } catch (_: Exception) { }
    }
}

/**
 * Pure selection logic for Smart Offline Mix: favorites and most-played
 * songs first, skipping anything already downloaded. Unit-testable.
 */
object OfflineMixSelector {
    /**
     * @param candidates play-count candidates (most-played first preferred)
     * @param starredIds ids the user starred on the server
     * @param downloadedIds ids already stored offline
     * @param maxSize how many songs the mix may hold
     */
    fun selectMix(
        candidates: List<MixCandidate>,
        starredIds: Set<String>,
        downloadedIds: Set<String>,
        maxSize: Int
    ): List<SongMeta> {
        if (maxSize <= 0) return emptyList()
        val seen = LinkedHashSet<String>()
        val out = ArrayList<SongMeta>(maxSize)
        // Starred songs not already downloaded go first (explicit favorites win).
        val starredFirst = candidates
            .filter { it.meta.id in starredIds && it.meta.id !in downloadedIds }
            .sortedWith(
                compareByDescending<MixCandidate> { it.playCount }
                    .thenBy { it.meta.title.lowercase() }
            )
        // Then everything else by play count.
        val rest = candidates
            .filter { it.meta.id !in starredIds && it.meta.id !in downloadedIds }
            .sortedWith(
                compareByDescending<MixCandidate> { it.playCount }
                    .thenBy { it.meta.title.lowercase() }
            )
        for (c in starredFirst + rest) {
            if (out.size >= maxSize) break
            if (seen.add(c.meta.id)) out.add(c.meta)
        }
        return out
    }
}
