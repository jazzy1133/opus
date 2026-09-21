package com.opus.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.opus.music.Graph
import com.opus.music.data.DownloadInfo
import com.opus.music.network.Song
import com.opus.music.player.PlayerManager
import com.opus.music.ui.CoverArt
import com.opus.music.ui.DownloadEvents
import com.opus.music.ui.EmptyBox
import com.opus.music.Session
import com.opus.music.ui.vm.DownloadsViewModel

private fun DownloadInfo.toSong(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    coverArt = coverArt,
    duration = duration,
    suffix = fileName.substringAfterLast('.', "mp3")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(nav: NavController) {
    val vm: DownloadsViewModel = viewModel()
    val items by vm.items.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall) },
                actions = { Text(vm.totalSize, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 16.dp)) }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                EmptyBox("No downloads yet.\nUse the ••• menu on any song to download it for offline listening.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                itemsIndexed(items) { index, info ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                val songs = items.map { it.toSong() }
                                PlayerManager.playSongs(songs, index, Graph.downloads)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoverArt(Session.client?.coverArtUrl(info.coverArt, 200), 52.dp, 8.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(info.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(info.artist.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = {
                            vm.delete(info.id)
                            DownloadEvents.bump()
                        }) {
                            Icon(Icons.Filled.Delete, "Remove download")
                        }
                    }
                }
            }
        }
    }
}
