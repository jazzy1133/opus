package com.opus.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.opus.music.Graph
import com.opus.music.network.Album
import com.opus.music.player.PlayerManager
import com.opus.music.ui.AlbumCard
import com.opus.music.ui.Routes
import com.opus.music.ui.SectionHeader
import com.opus.music.ui.vm.HomeViewModel
import com.opus.music.ui.vm.LoadState
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController) {
    val vm: HomeViewModel = viewModel()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Outro", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, "Refresh") }
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Filled.Settings, "Settings") }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val greeting = when (hour) {
                    in 5..11 -> "Good morning"
                    in 12..17 -> "Good afternoon"
                    else -> "Good evening"
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(greeting, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "What are we listening to today?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = {
                            vm.shufflePlay { songs ->
                                if (songs.isNotEmpty()) {
                                    PlayerManager.playSongs(songs.shuffled(), 0, Graph.downloads)
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Shuffle, null, modifier = Modifier.padding(end = 8.dp))
                            Text("Surprise me")
                        }
                    }
                }
            }
            item { AlbumRail("Recently added", vm.recent, nav) { vm.refresh() } }
            item { AlbumRail("Newest releases", vm.newest, nav) { vm.refresh() } }
            item { AlbumRail("Most played", vm.frequent, nav) { vm.refresh() } }
            item { AlbumRail("Discover", vm.random, nav) { vm.refresh() } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun AlbumRail(
    title: String,
    state: LoadState<List<Album>>,
    nav: NavController,
    onRetry: () -> Unit
) {
    when (state) {
        is LoadState.Loading -> Unit
        is LoadState.Err -> Column(Modifier.padding(vertical = 4.dp)) {
            SectionHeader(title)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Couldn't load.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onRetry) { Text("Retry") }
            }
        }
        is LoadState.Ok -> if (state.data.isNotEmpty()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                SectionHeader(title)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(state.data) { album ->
                        AlbumCard(album, onClick = { nav.navigate(Routes.album(album.id)) })
                    }
                }
            }
        }
    }
}
