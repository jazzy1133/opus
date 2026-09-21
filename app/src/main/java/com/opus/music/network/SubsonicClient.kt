package com.opus.music.network

import com.opus.music.data.ServerConfig
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID

private const val API_VERSION = "1.16.1"
private const val CLIENT_NAME = "Outro"

/** Adds Subsonic token auth (u/t/s/v/c/f) to every request. */
class AuthInterceptor(private val config: ServerConfig) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val token = md5Hex(config.password + salt)
        val url = chain.request().url.newBuilder()
            .addQueryParameter("u", config.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT_NAME)
            .addQueryParameter("f", "json")
            .build()
        return chain.proceed(chain.request().newBuilder().url(url).build())
    }
}

fun md5Hex(input: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

class SubsonicClient(val config: ServerConfig) {
    val baseUrl: String = config.baseUrl.trim().trimEnd('/') + "/"

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(config))
        .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(http)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    fun api(): SubsonicApi = retrofit.create(SubsonicApi::class.java)

    private fun authQuery(extra: String): String {
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val token = md5Hex(config.password + salt)
        val u = URLEncoder.encode(config.username, "UTF-8")
        return "u=$u&t=$token&s=$salt&v=$API_VERSION&c=$CLIENT_NAME$extra"
    }

    /** Direct stream URL for ExoPlayer / downloads (binary response). */
    fun streamUrl(songId: String, maxBitrate: Int = 0): String {
        val bitrateParam = if (maxBitrate > 0) "&maxBitRate=$maxBitrate" else ""
        return "${baseUrl}rest/stream.view?id=${URLEncoder.encode(songId, "UTF-8")}$bitrateParam&" + authQuery("")
    }

    /** Cover art URL for Coil / notifications. */
    fun coverArtUrl(coverArtId: String?, size: Int = 500): String? {
        if (coverArtId.isNullOrBlank()) return null
        return "${baseUrl}rest/getCoverArt.view?id=${URLEncoder.encode(coverArtId, "UTF-8")}&" +
            authQuery("&size=$size")
    }
}
