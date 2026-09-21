package com.opus.music.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.opus.music.Graph
import com.opus.music.Session
import com.opus.music.player.PlayerManager
import com.opus.music.ui.Routes
import com.opus.music.ui.theme.Brass
import kotlinx.coroutines.launch

private val BITRATE_OPTIONS = listOf(
    0 to "Original (no transcoding)",
    320 to "320 kbps",
    192 to "192 kbps",
    128 to "128 kbps",
)

private val SLEEP_OPTIONS = listOf(
    null to "Off",
    15 to "15 minutes",
    30 to "30 minutes",
    60 to "1 hour",
    90 to "1.5 hours",
)

private val SCREEN_LOCK_OPTIONS = listOf(
    "never" to "Never",
    "playing" to "When Playing",
    "always" to "Always",
)

private val CROSSFADE_OPTIONS = listOf(
    0 to "Off",
    2 to "2 seconds",
    4 to "4 seconds",
    6 to "6 seconds",
    8 to "8 seconds",
    12 to "12 seconds",
)

private val SLEEP_FADE_OPTIONS = listOf(1, 3, 5, 10)

private val MIX_SIZE_OPTIONS = listOf(25, 50, 100)

private fun bitrateLabel(bitrate: Int): String =
    BITRATE_OPTIONS.firstOrNull { it.first == bitrate }?.second ?: "Original (no transcoding)"

private fun screenLockLabel(value: String): String =
    SCREEN_LOCK_OPTIONS.firstOrNull { it.first == value }?.second ?: "Never"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController) {
    var section by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (section) {
                            "account" -> "Account"
                            "display" -> "Display & Interaction"
                            "library" -> "Library"
                            "player" -> "Player, Stream & Scrobble"
                            "equalizer" -> "Equalizer"
                            "swipe" -> "Swipe"
                            "artwork" -> "Artwork"
                            "support" -> "Support"
                            "license" -> "License"
                            "about" -> "About"
                            else -> "Settings"
                        },
                        color = Brass,
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (section != null) section = null else nav.popBackStack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            when (section) {
                null -> SettingsMainList(onOpen = { section = it })
                "account" -> AccountSection(nav)
                "display" -> DisplaySection()
                "library" -> LibrarySection()
                "player" -> PlayerSection()
                "equalizer" -> EqualizerSection()
                "swipe" -> SwipeSection()
                "artwork" -> ArtworkSection()
                "support" -> SupportSection()
                "license" -> LicenseSection()
                "about" -> AboutSection()
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(vertical = 4.dp)
    ) {
        content()
    }
}

@Composable
private fun SettingsRow(
    title: String,
    value: String? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(0.dp))
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun SettingsMainList(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    var screenLock by remember { mutableStateOf(Graph.settings.getPreventScreenLock()) }
    var showScreenLockDialog by remember { mutableStateOf(false) }

    // Top standalone card: Prevent Screen Lock (Amperfy-style)
    SettingsCard {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { showScreenLockDialog = true }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Prevent Screen Lock",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                screenLockLabel(screenLock),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    // Main group
    SettingsCard {
        SettingsRow("Account") { onOpen("account") }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        SettingsRow("Display & Interaction") { onOpen("display") }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        SettingsRow("Library") { onOpen("library") }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        SettingsRow("Player, Stream & Scrobble") { onOpen("player") }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        SettingsRow("Equalizer") { onOpen("equalizer") }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        SettingsRow("Swipe") { onOpen("swipe") }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        SettingsRow("Artwork") { onOpen("artwork") }
    }

    Spacer(Modifier.height(16.dp))

    // Support group
    SettingsCard {
        SettingsRow("Support") { onOpen("support") }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        SettingsRow("License") { onOpen("license") }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        SettingsRow("About") { onOpen("about") }
    }

    if (showScreenLockDialog) {
        AlertDialog(
            onDismissRequest = { showScreenLockDialog = false },
            title = { Text("Prevent Screen Lock") },
            text = {
                Column {
                    SCREEN_LOCK_OPTIONS.forEach { (value, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                screenLock = value
                                Graph.settings.setPreventScreenLock(value)
                                // Apply immediately
                                (context as? Activity)?.window?.let { win ->
                                    if (value == "always") {
                                        win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                    } else {
                                        win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                    }
                                }
                                showScreenLockDialog = false
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = screenLock == value, onClick = null)
                            Text(label, Modifier.padding(start = 8.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "\"When Playing\" keeps the screen on while the Now Playing screen is visible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showScreenLockDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun AccountSection(nav: NavController) {
    val scope = rememberCoroutineScope()
    val config = Session.config

    SettingsCard {
        ListItem(
            headlineContent = { Text(config?.baseUrl ?: "—") },
            supportingContent = { Text("Navidrome / Subsonic server") }
        )
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        ListItem(
            headlineContent = { Text(config?.username ?: "—") },
            supportingContent = { Text("Username") }
        )
    }
    Spacer(Modifier.height(16.dp))
    OutlinedButton(
        onClick = {
            scope.launch {
                Graph.settings.clear()
                Session.close()
                PlayerManager.setSleepTimer(null)
                nav.navigate(Routes.SETUP) {
                    popUpTo(Routes.MAIN) { inclusive = true }
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Log out")
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Logging out clears saved credentials on this device.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun DisplaySection() {
    var screenLock by remember { mutableStateOf(Graph.settings.getPreventScreenLock()) }
    SettingsCard {
        ListItem(
            headlineContent = { Text("Theme") },
            supportingContent = { Text("Jazzy Dark — always on, easy on the eyes") }
        )
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        ListItem(
            headlineContent = { Text("Prevent screen lock") },
            supportingContent = { Text(screenLockLabel(screenLock)) }
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Change \"Prevent Screen Lock\" from the main Settings list. \"When Playing\" keeps the display awake on the Now Playing screen.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LibrarySection() {
    val scope = rememberCoroutineScope()
    val bytes by produceState(initialValue = 0L) {
        value = try { Graph.downloads.totalBytes() } catch (_: Exception) { 0L }
    }
    var clearedTick by remember { mutableStateOf(0) }
    var mixEnabled by remember { mutableStateOf(Graph.settings.isOfflineMixEnabled()) }
    var mixSize by remember { mutableStateOf(Graph.settings.getOfflineMixSize()) }
    var wifiOnly by remember { mutableStateOf(Graph.settings.isOfflineMixWifiOnly()) }
    var lastSync by remember { mutableStateOf(Graph.stats.getLastMixSync()) }
    var syncing by remember { mutableStateOf(false) }
    var syncMsg by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current

    val mb = bytes / (1024.0 * 1024.0)

    SettingsCard {
        ListItem(
            headlineContent = { Text("Offline cache") },
            supportingContent = { Text(String.format("%.1f MB downloaded", mb)) }
        )
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        ListItem(
            headlineContent = { Text("Smart Offline Mix") },
            supportingContent = { Text("Auto-download favorites & most-played on Wi-Fi") },
            trailingContent = {
                Switch(
                    checked = mixEnabled,
                    onCheckedChange = {
                        mixEnabled = it
                        Graph.settings.setOfflineMixEnabled(it)
                    }
                )
            }
        )
        if (mixEnabled) {
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            ListItem(
                headlineContent = { Text("Mix size") },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MIX_SIZE_OPTIONS.forEach { n ->
                            OutlinedButton(
                                onClick = {
                                    mixSize = n
                                    Graph.settings.setOfflineMixSize(n)
                                },
                                colors = if (mixSize == n) ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ) else ButtonDefaults.outlinedButtonColors()
                            ) { Text("$n") }
                        }
                    }
                }
            )
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            ListItem(
                headlineContent = { Text("Wi-Fi only") },
                supportingContent = { Text("Never use mobile data for the mix") },
                trailingContent = {
                    Switch(
                        checked = wifiOnly,
                        onCheckedChange = {
                            wifiOnly = it
                            Graph.settings.setOfflineMixWifiOnly(it)
                        }
                    )
                }
            )
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            ListItem(
                headlineContent = { Text("Last synced") },
                supportingContent = {
                    Text(
                        if (lastSync == 0L) "Never"
                        else java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(lastSync))
                    )
                },
                trailingContent = {
                    OutlinedButton(
                        onClick = {
                            if (syncing) return@OutlinedButton
                            syncing = true
                            syncMsg = null
                            scope.launch {
                                val res = try {
                                    com.opus.music.data.OfflineMix.syncIfNeeded(ctx, force = true)
                                } catch (e: Exception) {
                                    com.opus.music.data.OfflineMix.SyncResult(0, 0, "error")
                                }
                                lastSync = try { Graph.stats.getLastMixSync() } catch (_: Exception) { lastSync }
                                syncMsg = when (res.message) {
                                    "ok" -> "Synced: ${res.downloaded} new song(s) downloaded."
                                    "disabled" -> "Turn the mix on first."
                                    "not logged in" -> "Log in first."
                                    "waiting for wi-fi" -> "Waiting for Wi-Fi."
                                    else -> "Sync: ${res.message}"
                                }
                                syncing = false
                            }
                        },
                        enabled = !syncing
                    ) { Text(if (syncing) "Syncing…" else "Sync now") }
                }
            )
        }
    }
    if (syncMsg != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            syncMsg!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(16.dp))
    OutlinedButton(
        onClick = {
            scope.launch {
                try {
                    val list = Graph.downloads.list()
                    list.forEach { Graph.downloads.delete(it.id) }
                    clearedTick++
                } catch (_: Exception) {}
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Clear offline cache")
    }
    if (clearedTick > 0) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Cache cleared.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlayerSection() {
    var bitrate by remember { mutableStateOf(Graph.settings.getStreamBitrate()) }
    var scrobble by remember { mutableStateOf(Graph.settings.isScrobbleEnabled()) }
    var gapless by remember { mutableStateOf(Graph.settings.isGaplessEnabled()) }
    var crossfade by remember { mutableStateOf(Graph.settings.getCrossfadeSec()) }
    var showBitrateDialog by remember { mutableStateOf(false) }
    var showCrossfadeDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    val sleepEndsAt by PlayerManager.sleepEndsAt.collectAsState()
    val sleepMinutes by PlayerManager.sleepMinutes.collectAsState()

    SettingsCard {
        Row(
            Modifier.fillMaxWidth().clickable { showBitrateDialog = true }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Streaming quality", style = MaterialTheme.typography.bodyLarge)
                Text(
                    bitrateLabel(bitrate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        ListItem(
            headlineContent = { Text("Scrobble to server") },
            supportingContent = { Text("Report played tracks to Navidrome") },
            trailingContent = {
                Switch(
                    checked = scrobble,
                    onCheckedChange = {
                        scrobble = it
                        Graph.settings.setScrobbleEnabled(it)
                    }
                )
            }
        )
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        ListItem(
            headlineContent = { Text("Gapless playback") },
            supportingContent = { Text("Seamless transitions between tracks") },
            trailingContent = {
                Switch(
                    checked = gapless,
                    onCheckedChange = {
                        gapless = it
                        Graph.settings.setGaplessEnabled(it)
                    }
                )
            }
        )
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        Row(
            Modifier.fillMaxWidth().clickable { showCrossfadeDialog = true }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Crossfade", style = MaterialTheme.typography.bodyLarge)
                Text(
                    CROSSFADE_OPTIONS.firstOrNull { it.first == crossfade }?.second ?: "Off",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        Row(
            Modifier.fillMaxWidth().clickable { showSleepDialog = true }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Sleep timer", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (sleepEndsAt != null) {
                        val mins = ((sleepEndsAt!! - System.currentTimeMillis()) / 60000).coerceAtLeast(1)
                        "Stops in ~$mins min"
                    } else "Off",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }

    if (showBitrateDialog) {
        AlertDialog(
            onDismissRequest = { showBitrateDialog = false },
            title = { Text("Streaming quality") },
            text = {
                Column {
                    BITRATE_OPTIONS.forEach { (value, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                bitrate = value
                                Graph.settings.setStreamBitrate(value)
                                showBitrateDialog = false
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = bitrate == value, onClick = null)
                            Text(label, Modifier.padding(start = 8.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Applies to songs played next. Downloaded songs always play at original quality.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showBitrateDialog = false }) { Text("Close") }
            }
        )
    }

    if (showCrossfadeDialog) {
        AlertDialog(
            onDismissRequest = { showCrossfadeDialog = false },
            title = { Text("Crossfade") },
            text = {
                Column {
                    CROSSFADE_OPTIONS.forEach { (value, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                crossfade = value
                                Graph.settings.setCrossfadeSec(value)
                                PlayerManager.refreshCrossfade()
                                showCrossfadeDialog = false
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = crossfade == value, onClick = null)
                            Text(label, Modifier.padding(start = 8.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Songs blend into each other like a DJ set.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showCrossfadeDialog = false }) { Text("Close") }
            }
        )
    }

    if (showSleepDialog) {
        var pendingMinutes by remember { mutableStateOf(sleepMinutes ?: 30) }
        var fadeMin by remember { mutableStateOf(Graph.settings.getSleepFadeMin()) }
        var endOfTrack by remember { mutableStateOf(Graph.settings.isSleepEndOfTrack()) }
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("Sleep timer") },
            text = {
                Column {
                    Text("Stop after",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SLEEP_OPTIONS.forEach { (value, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (value == null) {
                                    PlayerManager.setSleepTimer(null)
                                    showSleepDialog = false
                                } else pendingMinutes = value
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = value != null && pendingMinutes == value,
                                onClick = null
                            )
                            Text(label, Modifier.padding(start = 8.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Fade out over",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SLEEP_FADE_OPTIONS.forEach { m ->
                            val sel = fadeMin == m
                            OutlinedButton(
                                onClick = { fadeMin = m },
                                modifier = Modifier.weight(1f),
                                colors = if (sel) ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ) else ButtonDefaults.outlinedButtonColors()
                            ) { Text("${m}m") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { endOfTrack = !endOfTrack }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Stop at end of current track")
                        Switch(checked = endOfTrack, onCheckedChange = { endOfTrack = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Graph.settings.setSleepFadeMin(fadeMin)
                    Graph.settings.setSleepEndOfTrack(endOfTrack)
                    PlayerManager.setSleepTimer(pendingMinutes, fadeMin, endOfTrack)
                    showSleepDialog = false
                }) { Text("Start") }
            },
            dismissButton = {
                TextButton(onClick = { showSleepDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EqualizerSection() {
    Text(
        "Equalizer",
        style = MaterialTheme.typography.titleMedium,
        color = Brass
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Opus plays bit-perfect audio by default. A built-in 5-band equalizer is on the roadmap — for now, use your device's system equalizer for tone tweaks.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SwipeSection() {
    Text(
        "Swipe actions",
        style = MaterialTheme.typography.titleMedium,
        color = Brass
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Coming soon: swipe left on a song to play next, swipe right to add to the queue — like your favorite players, but smoother.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ArtworkSection() {
    var highQuality by remember { mutableStateOf(Graph.settings.isArtworkHighQuality()) }
    SettingsCard {
        ListItem(
            headlineContent = { Text("High-quality artwork") },
            supportingContent = { Text("Load larger cover art (uses more data)") },
            trailingContent = {
                Switch(
                    checked = highQuality,
                    onCheckedChange = {
                        highQuality = it
                        Graph.settings.setArtworkHighQuality(it)
                    }
                )
            }
        )
    }
}

@Composable
private fun SupportSection() {
    SettingsCard {
        ListItem(
            headlineContent = { Text("Opus Support") },
            supportingContent = { Text("Send diagnostics and feedback from the app") }
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Found a bug? Note what you were doing and which screen you were on — it helps fix things fast.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LicenseSection() {
    Text(
        "License",
        style = MaterialTheme.typography.titleMedium,
        color = Brass
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Opus is built with open-source libraries: Jetpack Compose, Media3, Coil, OkHttp, and Kotlinx Serialization. Thanks to their authors.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun AboutSection() {
    Text(
        "About",
        style = MaterialTheme.typography.titleMedium,
        color = Brass
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Opus 1.0.6 — a modern Subsonic client for Navidrome.\nDark, smooth, and yours.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
