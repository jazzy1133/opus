package com.opus.music.player

import android.content.ComponentName
import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.opus.music.Graph
import com.opus.music.Session
import com.opus.music.data.DownloadRepository
import com.opus.music.network.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-wide playback controller. Binds to [PlayerService] once and exposes
 * simple queue operations to the UI layer.
 */
object PlayerManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _currentSongId = MutableStateFlow<String?>(null)
    val currentSongId: StateFlow<String?> = _currentSongId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** Full Song objects for the current queue (for artwork ids, download, star...). */
    private val _queueSongs = MutableStateFlow<List<Song>>(emptyList())
    val queueSongs: StateFlow<List<Song>> = _queueSongs.asStateFlow()

    private var bound = false

    fun connect(context: Context) {
        if (bound) return
        bound = true
        try {
            val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener({
                try {
                    val c = future.get()
                    _controller.value = c
                    c.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) {
                            _isPlaying.value = playing
                        }
                        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                            _currentSongId.value = item?.mediaId
                        }
                    })
                    _isPlaying.value = c.isPlaying
                    _currentSongId.value = c.currentMediaItem?.mediaId
                } catch (e: Exception) {
                    bound = false
                }
            }, MoreExecutors.directExecutor())
        } catch (e: Exception) {
            bound = false
        }
    }

    /** Public builder used by the crossfade engine (uses the shared download repo). */
    fun mediaItemFor(song: Song): MediaItem {
        val downloads = try { Graph.downloads } catch (_: Exception) { null }
        return if (downloads != null) mediaItemFor(song, downloads)
        else {
            // Fallback: stream URL only.
            val client = Session.client
            val bitrate = try { Graph.settings.getStreamBitrate() } catch (_: Exception) { 0 }
            val uri = client?.streamUrl(song.id, bitrate) ?: ""
            MediaItem.Builder().setMediaId(song.id).setUri(uri).build()
        }
    }

    private fun mediaItemFor(song: Song, downloads: DownloadRepository): MediaItem {
        val client = Session.client
        val local = downloads.localUri(song.id)
        val bitrate = try { Graph.settings.getStreamBitrate() } catch (_: Exception) { 0 }
        val uri = local?.toString()
            ?: client?.streamUrl(song.id, bitrate)
            ?: ""
        val art = client?.coverArtUrl(song.coverArt, 500)?.toUri()
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(art)
            .build()
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun playSongs(songs: List<Song>, index: Int, downloads: DownloadRepository) {
        val c = _controller.value ?: return
        if (songs.isEmpty()) return
        EngineHolder.crossfade?.abortXfade()
        val items = songs.map { mediaItemFor(it, downloads) }
        _queueSongs.value = songs
        scope.launch(Dispatchers.Main) {
            c.setMediaItems(items, index.coerceIn(items.indices), 0L)
            c.prepare()
            c.play()
        }
    }

    fun playSingle(song: Song, downloads: DownloadRepository) =
        playSongs(listOf(song), 0, downloads)

    private fun queueIndexOfCurrent(): Int {
        val id = _currentSongId.value ?: return -1
        return _queueSongs.value.indexOfFirst { it.id == id }
    }

    /** Insert right after the currently playing song. */
    fun playNext(song: Song, downloads: DownloadRepository) {
        val c = _controller.value ?: return
        val item = mediaItemFor(song, downloads)
        EngineHolder.crossfade?.abortXfade()
        scope.launch(Dispatchers.Main) {
            if (c.mediaItemCount == 0) {
                _queueSongs.value = listOf(song)
                c.setMediaItem(item)
                c.prepare()
                c.play()
            } else {
                val at = (queueIndexOfCurrent() + 1).coerceIn(0, _queueSongs.value.size)
                val list = _queueSongs.value.toMutableList()
                list.add(at, song)
                _queueSongs.value = list
                c.addMediaItem(c.currentMediaItemIndex + 1, item)
            }
        }
    }

    /** Append to the current queue, or start playing if the queue is empty. */
    fun addToQueue(song: Song, downloads: DownloadRepository) {
        val c = _controller.value ?: return
        val item = mediaItemFor(song, downloads)
        EngineHolder.crossfade?.abortXfade()
        scope.launch(Dispatchers.Main) {
            if (c.mediaItemCount == 0) {
                _queueSongs.value = listOf(song)
                c.setMediaItem(item)
                c.prepare()
                c.play()
            } else {
                _queueSongs.value = _queueSongs.value + song
                c.addMediaItem(item)
            }
        }
    }

    fun togglePlayPause() {
        val c = _controller.value ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        if (EngineHolder.crossfade?.next() == true) return
        _controller.value?.seekToNextMediaItem()
    }

    fun previous() {
        if (EngineHolder.crossfade?.previous() == true) return
        _controller.value?.seekToPreviousMediaItem()
    }

    fun skipTo(index: Int) {
        if (EngineHolder.crossfade?.skipTo(index) == true) return
        _controller.value?.let {
            if (index in 0 until it.mediaItemCount) {
                it.seekTo(index, 0L)
                it.play()
            }
        }
    }
    fun seekTo(ms: Long) = _controller.value?.seekTo(ms)
    fun seekForward() =
        _controller.value?.let { it.seekTo((it.currentPosition + 10_000).coerceAtMost(it.duration.coerceAtLeast(0))) }

    fun seekBack() =
        _controller.value?.let { it.seekTo((it.currentPosition - 10_000).coerceAtLeast(0)) }

    fun toggleShuffle() {
        _controller.value?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        _controller.value?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun removeAt(index: Int) {
        EngineHolder.crossfade?.abortXfade()
        _controller.value?.removeMediaItem(index)
        val list = _queueSongs.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _queueSongs.value = list
        }
    }

    /**
     * Reorder the upcoming queue (songs after the current one) to [ids].
     * Used by Party Queue voting so the most-voted song plays next.
     */
    fun reorderUpcoming(ids: List<String>) {
        val c = _controller.value ?: return
        EngineHolder.crossfade?.abortXfade()
        scope.launch(Dispatchers.Main) {
            try {
                val cur = c.currentMediaItemIndex
                if (cur < 0) return@launch
                val songs = _queueSongs.value
                if (cur >= songs.size) return@launch
                val byId = songs.associateBy { it.id }
                val newTail = ids.mapNotNull { byId[it] }
                val newSongs = songs.subList(0, cur + 1) + newTail
                if (newSongs.size != songs.size) return@launch
                val items = newSongs.map { mediaItemFor(it) }
                val wasPlaying = c.isPlaying
                val pos = c.currentPosition
                c.setMediaItems(items, cur, pos)
                c.prepare()
                if (wasPlaying) c.play()
                _queueSongs.value = newSongs
            } catch (_: Exception) { }
        }
    }

    /** Push a changed crossfade length into the engine. */
    fun refreshCrossfade() {
        try {
            EngineHolder.crossfade?.fadeSec = Graph.settings.getCrossfadeSec()
        } catch (_: Exception) { }
    }

    // --- Sleep timer (smart fade) ---
    private var sleepJob: kotlinx.coroutines.Job? = null
    private val _sleepEndsAt = MutableStateFlow<Long?>(null)
    val sleepEndsAt: StateFlow<Long?> = _sleepEndsAt.asStateFlow()
    private val _sleepMinutes = MutableStateFlow<Int?>(null)
    val sleepMinutes: StateFlow<Int?> = _sleepMinutes.asStateFlow()

    /**
     * Start a sleep timer; playback pauses after [minutes].
     * The volume fades out over the last [fadeMinutes], and when
     * [endOfTrack] is true the timer instead ends at the end of the
     * current track. Pass 0/null to cancel.
     */
    fun setSleepTimer(minutes: Int?, fadeMinutes: Int = 5, endOfTrack: Boolean = false) {
        sleepJob?.cancel()
        sleepJob = null
        try { EngineHolder.crossfade?.cancelSleep() } catch (_: Exception) { }
        if (minutes == null || minutes <= 0) {
            _sleepEndsAt.value = null
            _sleepMinutes.value = null
            return
        }
        val totalMs: Long
        val fadeMs: Long
        if (endOfTrack) {
            val c = _controller.value
            val dur = try { c?.duration ?: -1L } catch (_: Exception) { -1L }
            val pos = try { c?.currentPosition ?: 0L } catch (_: Exception) { 0L }
            totalMs = if (dur > 0) (dur - pos).coerceAtLeast(30_000L) else minutes * 60_000L
            fadeMs = minOf(fadeMinutes * 60_000L, totalMs / 2)
        } else {
            totalMs = minutes * 60_000L
            fadeMs = minOf(fadeMinutes * 60_000L, totalMs / 2)
        }
        _sleepEndsAt.value = System.currentTimeMillis() + totalMs
        _sleepMinutes.value = minutes
        val engine = EngineHolder.crossfade
        if (engine != null) {
            engine.startSleepFade(totalMs, fadeMs)
            // Watchdog: clear the UI state once the fade is done.
            sleepJob = scope.launch {
                kotlinx.coroutines.delay(totalMs + 5_000L)
                _sleepEndsAt.value = null
                _sleepMinutes.value = null
            }
        } else {
            // Legacy fallback if the engine isn't up (service not bound).
            sleepJob = scope.launch {
                kotlinx.coroutines.delay(totalMs)
                try {
                    _controller.value?.pause()
                } catch (_: Exception) {}
                _sleepEndsAt.value = null
                _sleepMinutes.value = null
            }
        }
    }
}
