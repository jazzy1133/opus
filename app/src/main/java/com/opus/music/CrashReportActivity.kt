package com.opus.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opus.music.ui.theme.OpusTheme

/** Shown when the app crashes during startup; displays the stack trace. */
class CrashReportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra("trace") ?: "Unknown error"
        setContent {
            OpusTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                        Text("Outro crashed on startup",
                            style = MaterialTheme.typography.headlineSmall)
                        Text("Please screenshot this and send it to support.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp))
                        Text(trace, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

fun launchCrashReport(activity: ComponentActivity, e: Throwable) {
    val trace = android.util.Log.getStackTraceString(e)
    // Also write to a file for retrieval via file manager.
    try {
        val f = java.io.File(activity.filesDir, "crash.log")
        f.writeText(trace)
    } catch (_: Exception) {}
    val intent = android.content.Intent(activity, CrashReportActivity::class.java)
        .putExtra("trace", trace)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
    activity.startActivity(intent)
}
