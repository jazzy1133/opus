package com.opus.music.ui.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opus.music.Graph
import com.opus.music.Session
import com.opus.music.data.DownloadInfo
import com.opus.music.data.ServerConfig
import com.opus.music.data.SubsonicException
import com.opus.music.network.Album
import com.opus.music.network.AlbumDetail
import com.opus.music.network.Artist
import com.opus.music.network.ArtistDetail
import com.opus.music.data.MusicRepository
import com.opus.music.network.Playlist
import com.opus.music.network.PlaylistDetail
import com.opus.music.network.SearchResult3
import com.opus.music.network.Song
import com.opus.music.network.SubsonicClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ok<T>(val data: T) : LoadState<T>
    data class Err(val message: String) : LoadState<Nothing>
}

private fun errOf(e: Exception): LoadState.Err =
    LoadState.Err(e.message ?: "Something went wrong")

class HomeViewModel : ViewModel() {
    var recent by mutableStateOf<LoadState<List<Album>>>(LoadState.Loading)
        private set
    var newest by mutableStateOf<LoadState<List<Album>>>(LoadState.Loading)
        private set
    var frequent by mutableStateOf<LoadState<List<Album>>>(LoadState.Loading)
        private set
    var random by mutableStateOf<LoadState<List<Album>>>(LoadState.Loading)
        private set

    init { refresh() }

    fun refresh() {
        val repo = Session.music ?: return
        viewModelScope.launch {
            recent = load { repo.albumList("recent", 20) }
            newest = load { repo.albumList("newest", 20) }
            frequent = load { repo.albumList("frequent", 20) }
            random = load { repo.albumList("random", 20) }
        }
    }

    fun shufflePlay(onSongs: (List<Song>) -> Unit) {
        val repo = Session.music ?: return
        viewModelScope.launch {
            try {
                onSongs(repo.randomSongs(100))
            } catch (_: Exception) { }
        }
    }

    private suspend fun <T> load(block: suspend () -> T): LoadState<T> =
        try { LoadState.Ok(block()) } catch (e: Exception) { errOf(e) }
}

class LibraryViewModel : ViewModel() {
    var artists by mutableStateOf<LoadState<List<Artist>>>(LoadState.Loading)
        private set
    var albums by mutableStateOf<LoadState<List<Album>>>(LoadState.Loading)
        private set
    var playlists by mutableStateOf<LoadState<List<Playlist>>>(LoadState.Loading)
        private set
    var loved by mutableStateOf<LoadState<List<Song>>>(LoadState.Loading)
        private set

    init { refresh() }

    fun refresh() {
        val repo = Session.music ?: return
        viewModelScope.launch {
            artists = load { repo.artists() }
            albums = load { repo.albumList("alphabeticalByName", 300) }
            playlists = load { repo.playlists() }
            loved = load { repo.starred().song }
        }
    }

    private suspend fun <T> load(block: suspend () -> T): LoadState<T> =
        try { LoadState.Ok(block()) } catch (e: Exception) { errOf(e) }
}

class SearchViewModel : ViewModel() {
    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<LoadState<SearchResult3>?>(null)
        private set
    private var job: Job? = null

    fun onQueryChange(q: String) {
        query = q
        job?.cancel()
        if (q.isBlank()) {
            results = null
            return
        }
        job = viewModelScope.launch {
            delay(450)
            results = LoadState.Loading
            results = try {
                LoadState.Ok(Session.music?.search(q) ?: SearchResult3())
            } catch (e: Exception) {
                errOf(e)
            }
        }
    }
}

class DetailViewModel : ViewModel() {
    var artist by mutableStateOf<LoadState<ArtistDetail>>(LoadState.Loading)
        private set
    var album by mutableStateOf<LoadState<AlbumDetail>>(LoadState.Loading)
        private set
    var playlist by mutableStateOf<LoadState<PlaylistDetail>>(LoadState.Loading)
        private set

    fun loadArtist(id: String) {
        val repo = Session.music ?: return
        viewModelScope.launch {
            artist = LoadState.Loading
            artist = try { LoadState.Ok(repo.artist(id)) } catch (e: Exception) { errOf(e) }
        }
    }

    fun loadAlbum(id: String) {
        val repo = Session.music ?: return
        viewModelScope.launch {
            album = LoadState.Loading
            album = try { LoadState.Ok(repo.album(id)) } catch (e: Exception) { errOf(e) }
        }
    }

    fun loadPlaylist(id: String) {
        val repo = Session.music ?: return
        viewModelScope.launch {
            playlist = LoadState.Loading
            playlist = try { LoadState.Ok(repo.playlist(id)) } catch (e: Exception) { errOf(e) }
        }
    }
}

class DownloadsViewModel : ViewModel() {
    private val _items = MutableStateFlow<List<DownloadInfo>>(emptyList())
    val items: StateFlow<List<DownloadInfo>> = _items
    var totalSize by mutableStateOf("")
        private set

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _items.value = Graph.downloads.list()
            val bytes = Graph.downloads.totalBytes()
            totalSize = when {
                bytes < 1024 * 1024 -> "%d KB".format(bytes / 1024)
                bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / 1024f / 1024f)
                else -> "%.2f GB".format(bytes / 1024f / 1024f / 1024f)
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            Graph.downloads.delete(id)
            refresh()
        }
    }
}

class SetupViewModel : ViewModel() {
    var url by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun connect(onDone: () -> Unit) {
        if (busy) return
        error = null
        val u = url.trim()
        if (u.isBlank() || username.isBlank() || password.isEmpty()) {
            error = "Please fill in server URL, username and password."
            return
        }
        busy = true
        viewModelScope.launch {
            try {
                val config = ServerConfig(u, username.trim(), password)
                val client = SubsonicClient(config)
                val repo = MusicRepository(client)
                repo.ping()
                repo.getUser(username.trim())
                Graph.settings.save(u, username.trim(), password)
                Session.open(config)
                onDone()
            } catch (e: SubsonicException) {
                error = e.message
            } catch (e: Exception) {
                error = "Could not reach server: ${e.message}"
            } finally {
                busy = false
            }
        }
    }
}
