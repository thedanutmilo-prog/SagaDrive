package com.sagadrive.app

import com.sagadrive.app.feature.spotify.SpotifyLinks
import org.junit.Assert.*
import org.junit.Test

class SpotifyLinksTest {
    @Test fun acceptsSpotifyUri() =
        assertTrue(SpotifyLinks.isSupported("spotify:track:3HfKlhohNNTLIv2t9uvmzz"))
    @Test fun acceptsWebLink() =
        assertTrue(SpotifyLinks.isSupported("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC?si=abc"))
    @Test fun rejectsOtherHostsAndSchemes() {
        assertFalse(SpotifyLinks.isSupported("https://evil.example.com/track/x"))
        assertFalse(SpotifyLinks.isSupported("http://open.spotify.com/track/x"))
        assertFalse(SpotifyLinks.isSupported("https://open.spotify.com/"))
        assertFalse(SpotifyLinks.isSupported("spotify:track:short"))
        assertFalse(SpotifyLinks.isSupported(""))
    }
}
