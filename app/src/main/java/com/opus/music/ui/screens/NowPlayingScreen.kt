package com.opus.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import coil.compose.AsyncImage
import com.opus.music.Graph
import com.opus.music.Session
import com.opus.music.player.PlayerManager
import com.opus.music.ui.CoverArt
import com.opus.music.ui.DownloadEvents
import com.opus.music.ui.formatDurationMs
import com.opus.music.ui.rememberIsDownloaded
import com.opus.music.ui.theme.Brass
import com.opus.music.ui.vm.PlayerViewModel
import kotlinx.coroutines.launch
import androidx.media3.common.Player as M3Player

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(nav: NavController) {
    val vm: PlayerViewModel = viewModel()
    val ui by vm.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    var sliderOverride by remember { mutableStateOf<Float?>(null) }
    var favorite by remember(ui.songId) { mutableStateOf(ui.currentSong?.starred != null) }
    val downloaded = rememberIsDownloaded(ui.songId ?: "")
    var dragOffset by remember { mutableStateOf(0f) }

    fun dismiss() { nav.popBackStack() }

    // Prevent Screen Lock: "when playing" / "always" keeps display on here
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val mode = try { Graph.settings.getPreventScreenLock() } catch (_: Exception) { "never" }
        if (mode == "always" || mode == "playing") {
            (context as? Activity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val mode = try { Graph.settings.getPreventScreenLock() } catch (_: Exception) { "never" }
            if (mode != "always") {
                (context as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragOffset.toInt()) }
    ) {
    Scaffold(
        topBar = {
            Column {
                // Swipe-down handle: drag down anywhere on this handle to collapse
                // the full player back to the mini player.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (dragOffset > 180) dismiss()
                                    dragOffset = 0f
                                },
                                onVerticalDrag = { _, dy ->
                                    dragOffset = (dragOffset + dy).coerceIn(0f, 800f)
                                }
                            )
                        }
                        .padding(top = 8.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }
                TopAppBar(
                    title = { Text("Now Playing", style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        IconButton(onClick = { dismiss() }) { Icon(Icons.Filled.KeyboardArrowDown, "Collapse") }
                    },
                    actions = { PartyButton(nav) },
                    modifier = Modifier.pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragOffset > 180) dismiss()
                                dragOffset = 0f
                            },
                            onVerticalDrag = { _, dy ->
                                dragOffset = (dragOffset + dy).coerceIn(0f, 800f)
                            }
                        )
                    }
                )
            }
        }
    ) { padding ->
        if (ui.title.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Nothing playing", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Pick something from your library and it will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                CoverArt(ui.artworkUrl, 300.dp, 20.dp)
                Spacer(Modifier.height(24.dp))
                Text(
                    ui.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    ui.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    ui.album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))

                // Favorite + download
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = {
                        vm.toggleFavorite { starring -> favorite = starring }
                    }) {
                        Icon(
                            if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            "Favorite",
                            tint = if (favorite) Brass else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        val song = ui.currentSong ?: return@IconButton
                        scope.launch {
                            val client = Session.client ?: return@launch
                            if (downloaded) Graph.downloads.delete(song.id)
                            else Graph.downloads.download(song, client.streamUrl(song.id))
                            DownloadEvents.bump()
                        }
                    }) {
                        Icon(
                            if (downloaded) Icons.Filled.Download else Icons.Filled.Download,
                            if (downloaded) "Downloaded" else "Download",
                            tint = if (downloaded) Brass else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Seek bar
                val duration = ui.durationMs.coerceAtLeast(1)
                Slider(
                    value = sliderOverride ?: ui.positionMs.toFloat().coerceIn(0f, duration.toFloat()),
                    onValueChange = { sliderOverride = it },
                    onValueChangeFinished = {
                        sliderOverride?.let { PlayerManager.seekTo(it.toLong()) }
                        sliderOverride = null
                    },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDurationMs(sliderOverride?.toLong() ?: ui.positionMs), style = MaterialTheme.typography.bodySmall)
                    Text(formatDurationMs(ui.durationMs), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))

                // Main controls
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { PlayerManager.toggleShuffle() }) {
                        Icon(
                            Icons.Filled.Shuffle, "Shuffle",
                            tint = if (ui.shuffle) Brass else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { PlayerManager.previous() }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(36.dp))
                    }
                    FilledIconButton(
                        onClick = { PlayerManager.togglePlayPause() },
                        modifier = Modifier.size(76.dp)
                    ) {
                        Icon(
                            if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            "Play/Pause",
                            Modifier.size(40.dp)
                        )
                    }
                    IconButton(onClick = { PlayerManager.next() }, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Filled.SkipNext, "Next", Modifier.size(36.dp))
                    }
                    IconButton(onClick = { PlayerManager.cycleRepeat() }) {
                        val (icon, active) = when (ui.repeatMode) {
                            M3Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne to true
                            M3Player.REPEAT_MODE_ALL -> Icons.Filled.Repeat to true
                            else -> Icons.Filled.Repeat to false
                        }
                        Icon(icon, "Repeat", tint = if (active) Brass else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            if (ui.queue.isNotEmpty()) {
                item {
                    Text(
                        "Up next",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
                itemsIndexed(ui.queue) { index, entry ->
                    val isCurrent = index == ui.currentIndex
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .clickable { PlayerManager.skipTo(index) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (entry.artwork != null) {
                            AsyncImage(
                                entry.artwork, null,
                                modifier = Modifier.size(44.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            CoverArt(null, 44.dp, 8.dp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (isCurrent) Brass else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                entry.artist.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { PlayerManager.removeAt(index) }) {
                            Icon(Icons.Filled.Close, "Remove", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
    } // end swipe-dismiss Box
}
