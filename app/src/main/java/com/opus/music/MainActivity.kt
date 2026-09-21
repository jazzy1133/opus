package com.opus.music

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.opus.music.player.PlayerManager
import com.opus.music.ui.Nav
import com.opus.music.ui.theme.OpusTheme
import com.opus.music.ui.theme.ThemeController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install crash handler FIRST, before anything else.
        // Writes to app's external files dir (accessible via file manager, no permission needed).
        try {
            val logDir = getExternalFilesDir(null)
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { t, e ->
                try {
                    java.io.File(logDir, "crash.log").writeText(android.util.Log.getStackTraceString(e))
                } catch (_: Exception) {}
                defaultHandler?.uncaughtException(t, e)
            }
            // Marker file: proves onCreate was reached
            java.io.File(logDir, "started.log").writeText("onCreate reached")
        } catch (_: Exception) {}
        super.onCreate(savedInstanceState)
        // Initialize app singletons here (no custom Application class).
        // Everything is wrapped so a failure shows an error, never a silent crash.
        try {
            Graph.init(this)
        } catch (e: Throwable) {
            Log.e("MainActivity", "Graph.init failed", e)
            setContent {
                OpusTheme {
                    Surface(Modifier.fillMaxSize()) {
                        Text("Outro failed to start: ${e.message}")
                    }
                }
            }
            return
        }
        try {
            PlayerManager.connect(applicationContext)
        } catch (e: Throwable) {
            Log.e("MainActivity", "PlayerManager.connect failed", e)
            // Non-fatal: continue without playback service
        }
        // Apply "Prevent Screen Lock: Always" immediately on launch.
        try {
            val mode = Graph.settings.getPreventScreenLock()
            if (mode == "always") {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        } catch (_: Exception) {}
        // Seed the app-wide theme switch from saved preference (default: dark).
        try {
            ThemeController.init(Graph.settings.getThemeMode())
        } catch (_: Exception) {}
        // (Smart Offline Mix auto-sync is triggered from Nav() once the
        // session is restored, so it never races the login.)
        try {
            setContent {
                val themeMode by ThemeController.mode.collectAsState()
                OpusTheme(darkTheme = themeMode != ThemeController.LIGHT) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Nav()
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e("MainActivity", "setContent failed", e)
            // Last resort: try to show crash report
            try {
                launchCrashReport(this, e)
            } catch (_: Exception) {}
        }
    }
}
