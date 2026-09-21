package com.opus.music.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.opus.music.party.PartyQr
import com.opus.music.party.PartySession
import com.opus.music.player.PlayerManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyScreen(nav: NavController) {
    val ctx = LocalContext.current
    val active by PartySession.active.collectAsState()
    val url by PartySession.url.collectAsState()
    val version by PartySession.version.collectAsState()
    var maxAdds by remember { mutableIntStateOf(3) }
    var voting by remember { mutableStateOf(true) }

    // Refresh queue view when the session bumps its version.
    @Suppress("UNUSED_EXPRESSION") version
    val queue = remember(version, active) { PartySession.queueFor("host") }
    val guests = remember(version, active) { PartySession.guestCount() }
    val qr = remember(url) { url?.let { PartyQr.makeBitmap(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Party Queue") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!active) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Group, null,
                                    tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Let friends add songs",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Start a session and friends on your Wi-Fi scan the QR code " +
                                    "to add songs to your queue — no app needed. " +
                                    "You stay in control of playback.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Songs per guest")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (maxAdds > 0) maxAdds-- }) {
                                Icon(Icons.Filled.Remove, "Fewer")
                            }
                            Text("$maxAdds", style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { if (maxAdds < 20) maxAdds++ }) {
                                Icon(Icons.Filled.Add, "More")
                            }
                        }
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Guest voting")
                            Text("Most-voted songs play next",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = voting, onCheckedChange = { voting = it })
                    }
                }
                item {
                    Button(
                        onClick = {
                            val u = PartySession.start(ctx, maxAdds, voting)
                            if (u == null) {
                                Toast.makeText(ctx,
                                    "Couldn't start — connect to Wi-Fi first",
                                    Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Start party session") }
                }
            } else {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Guests scan to join",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            if (qr != null) {
                                Image(
                                    qr.asImageBitmap(), "Party QR code",
                                    Modifier.size(240.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(url.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("$guests guest${if (guests == 1) "" else "s"} connected",
                                style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { PartySession.stop() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) { Text("End session") }
                        }
                    }
                }
                if (queue.isNotEmpty()) {
                    item {
                        Text("Up next",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                    }
                    items(queue, key = { it.id }) { e ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(e.title, maxLines = 1,
                                    style = MaterialTheme.typography.bodyMedium)
                                if (e.artist != null) {
                                    Text(e.artist, maxLines = 1,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (e.votes > 0) {
                                Text("\uD83D\uDC4D ${e.votes}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else {
                    item {
                        Text("Queue is empty — waiting for the first guest pick.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Now Playing entry point for Party Queue. */
@Composable
fun PartyButton(nav: NavController) {
    IconButton(onClick = { nav.navigate(com.opus.music.ui.Routes.PARTY) }) {
        Icon(Icons.Filled.Group, "Party Queue")
    }
}
