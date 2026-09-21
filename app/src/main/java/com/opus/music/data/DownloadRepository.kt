package com.opus.music.data

import android.content.Context
import android.net.Uri
import com.opus.music.network.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@Serializable
data class DownloadInfo(
    val id: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val coverArt: String? = null,
    val duration: Int = 0,
    val fileName: String = ""
)

/** Simple offline store: audio files in app-private storage + a JSON index. */
class DownloadRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val http = OkHttpClient()

    private fun dir(): File = File(context.filesDir, "offline").apply { mkdirs() }
    private fun indexFile(): File = File(dir(), "downloads.json")

    private fun readIndex(): MutableMap<String, DownloadInfo> {
        val f = indexFile()
        if (!f.exists()) return mutableMapOf()
        return try {
            json.decodeFromString(
                MapSerializer(String.serializer(), DownloadInfo.serializer()),
                f.readText()
            ).toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun writeIndex(map: Map<String, DownloadInfo>) {
        indexFile().writeText(
            json.encodeToString(MapSerializer(String.serializer(), DownloadInfo.serializer()), map)
        )
    }

    suspend fun list(): List<DownloadInfo> = mutex.withLock {
        readIndex().values.sortedBy { it.title.lowercase() }
    }

    suspend fun isDownloaded(id: String): Boolean = mutex.withLock {
        val info = readIndex()[id] ?: return false
        File(dir(), info.fileName).exists()
    }

    /** Local file URI if this song is downloaded, else null. */
    fun localUri(id: String): Uri? {
        val idx = readIndex()
        val info = idx[id] ?: return null
        val f = File(dir(), info.fileName)
        return if (f.exists()) Uri.fromFile(f) else null
    }

    fun info(id: String): DownloadInfo? = readIndex()[id]

    private fun fileNameFor(song: Song): String {
        val ext = song.suffix?.takeIf { it.isNotBlank() } ?: "mp3"
        return "${song.id}.$ext"
    }

    suspend fun download(song: Song, streamUrl: String): DownloadInfo = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(streamUrl).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Download failed: HTTP ${resp.code}")
            val body = resp.body ?: throw Exception("Empty download")
            val out = File(dir(), fileNameFor(song))
            body.byteStream().use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val info = DownloadInfo(
            id = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            coverArt = song.coverArt,
            duration = song.duration,
            fileName = fileNameFor(song)
        )
        mutex.withLock {
            val idx = readIndex()
            idx[song.id] = info
            writeIndex(idx)
        }
        info
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val idx = readIndex()
            idx[id]?.let { File(dir(), it.fileName).delete() }
            idx.remove(id)
            writeIndex(idx)
        }
    }

    suspend fun totalBytes(): Long = withContext(Dispatchers.IO) {
        dir().walkTopDown().filter { it.isFile && it.name != "downloads.json" }.sumOf { it.length() }
    }
}
