package com.videapp.mobile.core.sanitizer

import com.videapp.mobile.domain.model.SocialPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for UrlSanitizer validating URL extraction, cleaning and normalization
 * across YouTube, X (Twitter), Facebook and Instagram.
 */
class UrlSanitizerTest {

    @Test
    fun `sanitize YouTube standard video cleans tracking parameters and keeps video id`() {
        val raw = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=AbCdEfGhIj1234&utm_source=share"
        val sanitized = UrlSanitizer.sanitize(raw)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", sanitized)
        assertEquals(SocialPlatform.YOUTUBE, UrlSanitizer.identifyPlatform(sanitized!!))
    }

    @Test
    fun `sanitize YouTube standard video preserves start timestamp`() {
        val raw = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=45s&si=tracking123"
        val sanitized = UrlSanitizer.sanitize(raw)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=45s", sanitized)
    }

    @Test
    fun `sanitize YouTube Shorts cleans tracking and feature params`() {
        val raw = "https://www.youtube.com/shorts/5qap5aO4i9A?feature=share&si=xYz123"
        val sanitized = UrlSanitizer.sanitize(raw)
        assertEquals("https://www.youtube.com/shorts/5qap5aO4i9A", sanitized)
    }

    @Test
    fun `sanitize YouTube shortlink cleans tracking`() {
        val raw = "https://youtu.be/dQw4w9WgXcQ?si=abcdef12345"
        val sanitized = UrlSanitizer.sanitize(raw)
        assertEquals("https://youtu.be/dQw4w9WgXcQ", sanitized)
    }

    @Test
    fun `sanitize YouTube mobile url normalizes domain`() {
        val raw = "https://m.youtube.com/watch?v=dQw4w9WgXcQ&si=mobileTrack"
        val sanitized = UrlSanitizer.sanitize(raw)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", sanitized)
    }

    @Test
    fun `sanitize Twitter and X normalizes domain to twitter_com and strips share trackers`() {
        val rawTwitter = "https://twitter.com/NASA/status/1234567890123456789?s=20&t=k1234XYZ"
        val sanitizedTwitter = UrlSanitizer.sanitize(rawTwitter)
        assertEquals("https://twitter.com/NASA/status/1234567890123456789", sanitizedTwitter)
        assertEquals(SocialPlatform.TWITTER_X, UrlSanitizer.identifyPlatform(sanitizedTwitter!!))

        val rawX = "https://x.com/SpaceX/status/9876543210?s=46"
        val sanitizedX = UrlSanitizer.sanitize(rawX)
        assertEquals("https://twitter.com/SpaceX/status/9876543210", sanitizedX)
    }

    @Test
    fun `sanitize Facebook Watch preserves video id and cleans tracking`() {
        val raw = "https://m.facebook.com/watch/?v=1234567890&ref=sharing&mibextid=rS40aB"
        val sanitized = UrlSanitizer.sanitize(raw)
        assertEquals("https://www.facebook.com/watch/?v=1234567890", sanitized)
        assertEquals(SocialPlatform.FACEBOOK, UrlSanitizer.identifyPlatform(sanitized!!))
    }

    @Test
    fun `sanitize Facebook Reels strips mibextid tracking and normalizes domain`() {
        val raw = "https://m.facebook.com/reel/987654321098765?mibextid=roILVy"
        val sanitized = UrlSanitizer.sanitize(raw)
        assertEquals("https://www.facebook.com/reel/987654321098765", sanitized)
    }

    @Test
    fun `sanitize Instagram Reel cleans igsh tracking param`() {
        val raw = "https://www.instagram.com/reel/C3_aBcDeF12/?igsh=MWQ1ZGUxMzBkMA=="
        val sanitized = UrlSanitizer.sanitize(raw)
        assertEquals("https://www.instagram.com/reel/C3_aBcDeF12/", sanitized)
        assertEquals(SocialPlatform.INSTAGRAM, UrlSanitizer.identifyPlatform(sanitized!!))
    }

    @Test
    fun `sanitize Instagram post normalizes mobile domain and cleans igshid`() {
        val raw = "https://m.instagram.com/p/C2_xYzAbcde/?igshid=MzRlODBiNWFlZA=="
        val sanitized = UrlSanitizer.sanitize(raw)
        assertEquals("https://www.instagram.com/p/C2_xYzAbcde/", sanitized)
    }

    @Test
    fun `sanitize extracts URL from natural prose and shared app text`() {
        val sharedText = "Mira este video genial! https://youtu.be/dQw4w9WgXcQ?si=share123 - Imperdible!"
        val sanitized = UrlSanitizer.sanitize(sharedText)
        assertEquals("https://youtu.be/dQw4w9WgXcQ", sanitized)
    }

    @Test
    fun `sanitize handles trailing punctuation smoothly`() {
        val sharedText = "Visita (https://x.com/NASA/status/1122334455?s=20)."
        val sanitized = UrlSanitizer.sanitize(sharedText)
        assertEquals("https://twitter.com/NASA/status/1122334455", sanitized)
    }

    @Test
    fun `sanitize returns null for invalid or empty text`() {
        assertNull(UrlSanitizer.sanitize(null))
        assertNull(UrlSanitizer.sanitize(""))
        assertNull(UrlSanitizer.sanitize("   "))
        assertNull(UrlSanitizer.sanitize("Texto aleatorio sin ningún enlace web"))
    }
}

