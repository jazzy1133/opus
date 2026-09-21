package com.opus.music.network

import retrofit2.http.GET
import retrofit2.http.Query

interface SubsonicApi {
    @GET("rest/ping.view")
    suspend fun ping(): SubsonicResponse

    @GET("rest/getUser.view")
    suspend fun getUser(@Query("username") username: String): SubsonicResponse

    @GET("rest/getArtists.view")
    suspend fun getArtists(): SubsonicResponse

    @GET("rest/getArtist.view")
    suspend fun getArtist(@Query("id") id: String): SubsonicResponse

    @GET("rest/getAlbum.view")
    suspend fun getAlbum(@Query("id") id: String): SubsonicResponse

    @GET("rest/getAlbumList.view")
    suspend fun getAlbumList(
        @Query("type") type: String,
        @Query("size") size: Int = 30,
        @Query("offset") offset: Int = 0
    ): SubsonicResponse

    @GET("rest/getPlaylists.view")
    suspend fun getPlaylists(): SubsonicResponse

    @GET("rest/getPlaylist.view")
    suspend fun getPlaylist(@Query("id") id: String): SubsonicResponse

    @GET("rest/search3.view")
    suspend fun search3(
        @Query("query") query: String,
        @Query("artistCount") artistCount: Int = 8,
        @Query("albumCount") albumCount: Int = 8,
        @Query("songCount") songCount: Int = 25
    ): SubsonicResponse

    @GET("rest/getRandomSongs.view")
    suspend fun getRandomSongs(@Query("size") size: Int = 50): SubsonicResponse

    @GET("rest/getStarred.view")
    suspend fun getStarred(): SubsonicResponse

    @GET("rest/star.view")
    suspend fun star(@Query("id") id: String): SubsonicResponse

    @GET("rest/unstar.view")
    suspend fun unstar(@Query("id") id: String): SubsonicResponse

    @GET("rest/scrobble.view")
    suspend fun scrobble(
        @Query("id") id: String,
        @Query("time") timeMs: Long,
        @Query("submission") submission: Boolean = true
    ): SubsonicResponse
}
