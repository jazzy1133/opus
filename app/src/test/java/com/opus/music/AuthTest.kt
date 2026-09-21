package com.opus.music

import com.opus.music.data.ServerConfig
import com.opus.music.network.SubsonicClient
import com.opus.music.network.md5Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for Subsonic auth + URL building (no network needed). */
class AuthTest {

    @Test
    fun md5KnownVector() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", md5Hex("hello"))
    }

    @Test
    fun streamUrlHasAuthParams() {
        val client = SubsonicClient(ServerConfig("http://example.com:4533/", "user", "pass"))
        val url = client.streamUrl("123")
        assertTrue(url.startsWith("http://example.com:4533/rest/stream.view?id=123&"))
        assertTrue(url.contains("u=user"))
        assertTrue(url.contains("v=1.16.1"))
        assertTrue(url.contains("c=Opus"))
        assertTrue(Regex("[?&]t=[0-9a-f]{32}").containsMatchIn(url))
        assertTrue(Regex("[?&]s=[0-9a-f]{12}").containsMatchIn(url))
    }

    @Test
    fun streamUrlEncodesId() {
        val client = SubsonicClient(ServerConfig("http://example.com:4533", "u", "p"))
        val url = client.streamUrl("a b/c")
        assertTrue(url.contains("id=a+b%2Fc"))
    }

    @Test
    fun coverArtUrlNullWhenBlank() {
        val client = SubsonicClient(ServerConfig("http://example.com:4533", "u", "p"))
        assertNull(client.coverArtUrl(null))
        assertNull(client.coverArtUrl(""))
        val url = client.coverArtUrl("abc", 300)!!
        assertTrue(url.contains("getCoverArt.view?id=abc"))
        assertTrue(url.contains("size=300"))
    }

    @Test
    fun baseUrlNormalized() {
        val c = SubsonicClient(ServerConfig("http://h:4533///", "u", "p"))
        assertEquals("http://h:4533/", c.baseUrl)
    }
}
