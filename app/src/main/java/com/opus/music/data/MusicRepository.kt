package com.opus.music.data

import com.opus.music.network.Album
import com.opus.music.network.AlbumDetail
import com.opus.music.network.Artist
import com.opus.music.network.ArtistDetail
import com.opus.music.network.Payload
import com.opus.music.network.Playlist
import com.opus.music.network.PlaylistDetail
import com.opus.music.network.SearchResult3
import com.opus.music.network.Song
import com.opus.music.network.Starred
import com.opus.music.network.SubsonicClient
import com.opus.music.network.SubsonicResponse

class SubsonicException(message: String) : Exception(message)

class MusicRepository(private val client: SubsonicClient) {
    private val api get() = client.api()

    private suspend fun <T> call(
        block: suspend () -> SubsonicResponse,
        extract: (Payload) -> T
    ): T {
        val res = try {
            block().response
        } catch (e: Exception) {
            throw SubsonicException(e.message ?: "Network error")
        }
        if (res.status != "ok") {
            throw SubsonicException(res.error?.message?.ifBlank { null } ?: "Server error (${res.error?.code ?: "?"})")
        }
        return extract(res)
    }

    suspend fun ping() = call({ api.ping() }) { }

    suspend fun getUser(username: String) = call({ api.getUser(username) }) { }

    suspend fun artists(): List<Artist> = call({ api.getArtists() }) { res ->
        res.artists?.index?.flatMap { it.artist }.orEmpty().sortedBy { it.name.lowercase() }
    }

    suspend fun artist(id: String): ArtistDetail =
        call({ api.getArtist(id) }) { it.artist ?: ArtistDetail() }

    suspend fun album(id: String): AlbumDetail =
        call({ api.getAlbum(id) }) { it.album ?: AlbumDetail() }

    /** type: recent | newest | frequent | random | alphabeticalByName | starred */
    suspend fun albumList(type: String, size: Int = 30): List<Album> =
        call({ api.getAlbumList(type, size) }) {
            (it.albumList ?: it.albumList2)?.album.orEmpty()
        }

    suspend fun playlists(): List<Playlist> =
        call({ api.getPlaylists() }) { it.playlists?.playlist.orEmpty() }

    suspend fun playlist(id: String): PlaylistDetail =
        call({ api.getPlaylist(id) }) { it.playlist ?: PlaylistDetail() }

    suspend fun search(query: String): SearchResult3 =
        call({ api.search3(query) }) { it.searchResult3 ?: SearchResult3() }

    suspend fun randomSongs(size: Int = 50): List<Song> =
        call({ api.getRandomSongs(size) }) { it.randomSongs?.song.orEmpty() }

    suspend fun starred(): Starred =
        call({ api.getStarred() }) { it.starred ?: Starred() }

    suspend fun star(id: String) = call({ api.star(id) }) { }
    suspend fun unstar(id: String) = call({ api.unstar(id) }) { }

    suspend fun scrobble(id: String, timeMs: Long) =
        call({ api.scrobble(id, timeMs) }) { }
}
