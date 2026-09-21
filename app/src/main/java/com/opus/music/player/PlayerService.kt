package com.opus.music.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.opus.music.Graph

/** Foreground playback service hosting ExoPlayer + MediaSession. */
class PlayerService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        try {
            super.onCreate()
        } catch (e: Throwable) {
            android.util.Log.e("PlayerService", "super.onCreate failed", e)
        }
        try {
            val player = ExoPlayer.Builder(this)
                .setHandleAudioBecomingNoisy(true)
                .build()
            session = MediaSession.Builder(this, player).build()
            // Companion-player crossfade engine + smart sleep fade.
            try {
                val engine = CrossfadeEngine(
                    applicationContext,
                    player,
                    songLookup = { id -> PlayerManager.queueSongs.value.firstOrNull { it.id == id } },
                    mediaItemFor = { song -> PlayerManager.mediaItemFor(song) }
                )
                engine.fadeSec = try { Graph.settings.getCrossfadeSec() } catch (_: Exception) { 0 }
                EngineHolder.crossfade = engine
            } catch (e: Throwable) {
                android.util.Log.e("PlayerService", "CrossfadeEngine init failed", e)
            }
        } catch (e: Throwable) {
            android.util.Log.e("PlayerService", "ExoPlayer init failed", e)
            // Leave session null; controller will get null session gracefully.
            session = null
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        try { EngineHolder.crossfade?.release() } catch (_: Exception) { }
        EngineHolder.crossfade = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
