package com.opus.music.party

import android.content.Context
import android.net.wifi.WifiManager
import com.opus.music.Graph
import com.opus.music.Session
import com.opus.music.network.Song
import com.opus.music.player.EngineHolder
import com.opus.music.player.PlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket

/** Data about one queue entry, as seen by a guest. */
data class GuestQueueEntry(
    val id: String,
    val title: String,
    val artist: String?,
    val votes: Int,
    val votedByMe: Boolean,
    val addedByMe: Boolean
)

/**
 * Host-side party state. Singleton: one session at a time.
 * The embedded web server (PartyServer) calls into this from its own
 * threads; all PlayerManager calls are marshalled to the main thread.
 */
object PartySession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _url = MutableStateFlow<String?>(null)
    val url: StateFlow<String?> = _url.asStateFlow()

    /** Bumped whenever queue/votes change so the UI refreshes. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    val policy = PartyPolicy()
    private var server: PartyServer? = null
    // Accessed from NanoHTTPD server threads as well as the main thread:
    // concurrent collections keep guest actions race-free.
    private val guests = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val addedBy = java.util.concurrent.ConcurrentHashMap<String, String>() // songId -> guest ip
    private val searchCache =
        java.util.concurrent.ConcurrentHashMap<String, List<Song>>() // guest ip -> last results

    fun guestCount(): Int = guests.size

    fun wifiIp(ctx: Context): String? {
        return try {
            @Suppress("DEPRECATION")
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            if (ip == 0) null
            else "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
        } catch (_: Exception) { null }
    }

    private fun freePort(): Int {
        return try {
            ServerSocket(0).use { it.localPort }
        } catch (_: Exception) { 8080 }
    }

    /** Start hosting. Returns the guest URL, or null on failure. */
    fun start(ctx: Context, maxAddsPerGuest: Int, votingEnabled: Boolean): String? {
        if (_active.value) return _url.value
        return try {
            val ip = wifiIp(ctx) ?: return null
            val port = freePort()
            policy.maxAddsPerGuest = maxAddsPerGuest.coerceIn(0, 20)
            policy.votingEnabled = votingEnabled
            val srv = PartyServer(port, this)
            srv.start()
            server = srv
            val u = "http://$ip:$port/"
            _url.value = u
            _active.value = true
            bump()
            u
        } catch (e: Exception) {
            try { server?.stop() } catch (_: Exception) { }
            server = null
            null
        }
    }

    fun stop() {
        try { server?.stop() } catch (_: Exception) { }
        server = null
        _active.value = false
        _url.value = null
        guests.clear()
        addedBy.clear()
        searchCache.clear()
        policy.reset()
        bump()
    }

    private fun bump() { _version.value = _version.value + 1 }

    internal fun noteGuest(ip: String) {
        if (guests.add(ip)) bump()
    }

    // ---------------- guest actions (called from server threads) ----------------

    /** @return ok | limit | duplicate | notfound | inactive */
    fun addSong(songId: String, guestIp: String): String {
        if (!_active.value) return "inactive"
        noteGuest(guestIp)
        val song: Song = findSong(songId, guestIp) ?: return "notfound"
        if (PlayerManager.queueSongs.value.any { it.id == songId }) return "duplicate"
        // Atomic check-and-count so two simultaneous adds can't exceed the limit.
        if (!policy.tryAdd(guestIp)) return "limit"
        addedBy[songId] = guestIp
        scope.launch {
            try {
                EngineHolder.crossfade?.abortXfade()
                PlayerManager.addToQueue(song, Graph.downloads)
            } catch (_: Exception) { }
            reorderByVotes()
            bump()
        }
        return "ok"
    }

    private fun findSong(songId: String, guestIp: String): Song? {
        // 1) current queue (fast, no network)
        PlayerManager.queueSongs.value.firstOrNull { it.id == songId }?.let { return it }
        // 2) this guest's recent search results
        searchCache[guestIp]?.firstOrNull { it.id == songId }?.let { return it }
        // 3) any cached search results
        searchCache.values.firstNotNullOfOrNull { list -> list.firstOrNull { it.id == songId } }
            ?.let { return it }
        return null
    }

    /** Toggle a vote. Returns Pair(votedNow, totalVotes). */
    fun vote(songId: String, guestIp: String): Pair<Boolean, Int> {
        if (!_active.value) return false to 0
        noteGuest(guestIp)
        val voted = policy.toggleVote(songId, guestIp)
        scope.launch { reorderByVotes(); bump() }
        return voted to policy.voteCount(songId)
    }

    /** Most-voted songs bubble to play next (host keeps skip control). */
    fun reorderByVotes() {
        if (!policy.votingEnabled || !_active.value) return
        try {
            val songs = PlayerManager.queueSongs.value
            if (songs.size < 2) return
            val curId = PlayerManager.currentSongId.value
            val curIdx = songs.indexOfFirst { it.id == curId }.coerceAtLeast(0)
            val tail = songs.drop(curIdx + 1)
            if (tail.isEmpty()) return
            val ordered = policy.orderByVotes(tail.map { it.id })
            val byId = tail.associateBy { it.id }
            val newTail = ordered.mapNotNull { byId[it] }
            if (newTail.map { it.id } != tail.map { it.id }) {
                PlayerManager.reorderUpcoming(newTail.map { it.id })
            }
        } catch (_: Exception) { }
        bump()
    }

    fun queueFor(guestIp: String): List<GuestQueueEntry> {
        val songs = PlayerManager.queueSongs.value
        val curId = PlayerManager.currentSongId.value
        val curIdx = songs.indexOfFirst { it.id == curId }.coerceAtLeast(0)
        return songs.drop(curIdx + 1).map { s ->
            GuestQueueEntry(
                id = s.id,
                title = s.title,
                artist = s.artist,
                votes = policy.voteCount(s.id),
                votedByMe = policy.hasVoted(s.id, guestIp),
                addedByMe = addedBy[s.id] == guestIp
            )
        }
    }

    fun nowPlaying(): Song? {
        val id = PlayerManager.currentSongId.value ?: return null
        return PlayerManager.queueSongs.value.firstOrNull { it.id == id }
    }

    fun search(q: String, guestIp: String): List<Song> {
        if (q.isBlank()) return emptyList()
        return try {
            runBlocking {
                val r = Session.music?.search(q) ?: return@runBlocking emptyList()
                val songs = r.song ?: emptyList()
                val albums = r.album ?: emptyList()
                // Also expand matching albums into their songs (one level).
                val out = songs.toMutableList()
                for (a in albums.take(3)) {
                    try {
                        out.addAll(Session.music?.album(a.id)?.song ?: emptyList())
                    } catch (_: Exception) { }
                    if (out.size >= 30) break
                }
                out.take(30)
            }.also { searchCache[guestIp] = it }
        } catch (_: Exception) { emptyList() }
    }
}
