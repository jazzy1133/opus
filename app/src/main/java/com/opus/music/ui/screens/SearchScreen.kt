package com.opus.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.opus.music.Graph
import com.opus.music.player.PlayerManager
import com.opus.music.ui.AlbumCard
import com.opus.music.ui.ArtistRow
import com.opus.music.ui.EmptyBox
import com.opus.music.ui.ErrorBox
import com.opus.music.ui.LoadingBox
import com.opus.music.ui.Routes
import com.opus.music.ui.SectionHeader
import com.opus.music.ui.SongRow
import com.opus.music.ui.components.OpusTextField
import com.opus.music.ui.vm.LoadState
import com.opus.music.ui.vm.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(nav: NavController) {
    val vm: SearchViewModel = viewModel()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Search", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall) }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OpusTextField(
                value = vm.query,
                onValueChange = vm::onQueryChange,
                label = "Search",
                placeholder = "Artists, albums, songs...",
                singleLine = true,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when (val r = vm.results) {
                null -> EmptyBox("Search your whole music library.")
                is LoadState.Loading -> LoadingBox()
                is LoadState.Err -> ErrorBox(r.message) { vm.onQueryChange(vm.query) }
                is LoadState.Ok -> {
                    val res = r.data
                    if (res.artist.isEmpty() && res.album.isEmpty() && res.song.isEmpty()) {
                        EmptyBox("No results for \"${vm.query}".trim() + "\"")
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            if (res.song.isNotEmpty()) {
                                item { SectionHeader("Songs") }
                                items(res.song) { song ->
                                    SongRow(
                                        song = song,
                                        onClick = { PlayerManager.playSongs(res.song, res.song.indexOf(song), Graph.downloads) },
                                        onGoAlbum = song.albumId?.let { id -> { nav.navigate(Routes.album(id)) } },
                                        onGoArtist = song.artistId?.let { id -> { nav.navigate(Routes.artist(id)) } }
                                    )
                                }
                            }
                            if (res.album.isNotEmpty()) {
                                item { SectionHeader("Albums") }
                                item {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        items(res.album) { album ->
                                            AlbumCard(album, onClick = { nav.navigate(Routes.album(album.id)) })
                                        }
                                    }
                                }
                            }
                            if (res.artist.isNotEmpty()) {
                                item { SectionHeader("Artists") }
                                items(res.artist) { artist ->
                                    ArtistRow(artist) { nav.navigate(Routes.artist(artist.id)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
