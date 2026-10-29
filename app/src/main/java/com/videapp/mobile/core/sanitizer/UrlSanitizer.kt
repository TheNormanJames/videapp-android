package com.videapp.mobile.core.sanitizer

import com.videapp.mobile.domain.model.SocialPlatform
import java.net.URI

/**
 * High-performance O(1) URL Sanitizer and Normalizer.
 * Extracts clean video URLs from shared text snippets and strips tracking query parameters
 * (e.g. ?si=, ?utm_*, fbclid=, ref=, igsh=) while preserving essential media identifiers.
 */
object UrlSanitizer {

    // Regex to detect any valid http/https URL in arbitrary text
    private val URL_REGEX = Regex(
        pattern = """https?://[^\s<>"'{}|\\^`]+""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    // Unnecessary tracking parameters across social platforms
    private val TRACKING_PARAMS = setOf(
        "si",
        "utm_source",
        "utm_medium",
        "utm_campaign",
        "utm_term",
        "utm_content",
        "fbclid",
        "ref",
        "ref_src",
        "s", // Twitter/X share tracking
        "t", // Twitter/X tracking (unless YouTube timestamp)
        "igsh", // Instagram share tracking
        "igshid",
        "mibextid", // Facebook mobile tracking
        "share_id",
        "locale",
        "feature"
    )

    /**
     * Extracts and sanitizes the first valid video URL found in [sharedContent].
     * Returns null if no valid URL is found.
     */
    fun sanitize(sharedContent: String?): String? {
        if (sharedContent.isNullOrBlank()) return null

        val rawUrl = extractRawUrl(sharedContent) ?: return null
        return normalizeAndCleanUrl(rawUrl)
    }

    /**
     * Identifies the social platform for a given URL.
     */
    fun identifyPlatform(url: String): SocialPlatform {
        val lower = url.lowercase()
        return when {
            lower.contains("youtube.com") || lower.contains("youtu.be") -> SocialPlatform.YOUTUBE
            lower.contains("twitter.com") || lower.contains("x.com") -> SocialPlatform.TWITTER_X
            lower.contains("facebook.com") || lower.contains("fb.watch") -> SocialPlatform.FACEBOOK
            lower.contains("instagram.com") -> SocialPlatform.INSTAGRAM
            lower.contains("tiktok.com") -> SocialPlatform.TIKTOK
            else -> SocialPlatform.UNKNOWN
        }
    }

    private fun extractRawUrl(text: String): String? {
        val match = URL_REGEX.find(text) ?: return null
        var foundUrl = match.value.trim()

        // Clean trailing punctuation attached from surrounding prose (e.g. "https://youtu.be/xyz.")
        foundUrl = foundUrl.trimEnd('.', ',', ';', '!', '?', ')', ']', '}')

        return if (foundUrl.startsWith("http://", ignoreCase = true) ||
            foundUrl.startsWith("https://", ignoreCase = true)
        ) {
            foundUrl
        } else {
            null
        }
    }

    private fun normalizeAndCleanUrl(rawUrl: String): String {
        return try {
            val uri = URI(rawUrl)
            val scheme = uri.scheme?.lowercase() ?: "https"
            var host = uri.host?.lowercase() ?: ""
            var path = uri.path ?: ""
            val query = uri.query

            // 1. Normalize Hostnames
            host = when {
                host == "mobile.twitter.com" || host == "twitter.com" -> "x.com"
                host == "m.facebook.com" || host == "mobile.facebook.com" || host == "web.facebook.com" -> "www.facebook.com"
                host == "m.youtube.com" -> "www.youtube.com"
                host == "m.instagram.com" -> "www.instagram.com"
                else -> host
            }

            // 2. Identify platform for domain-specific query handling
            val isYouTube = host.contains("youtube.com") || host == "youtu.be"
            val isFacebook = host.contains("facebook.com") || host.contains("fb.watch")

            // 3. Filter query parameters
            val cleanedQuery = if (!query.isNullOrEmpty()) {
                val params = query.split("&")
                val keptParams = mutableListOf<String>()

                for (param in params) {
                    val key = param.substringBefore("=").lowercase()
                    if (key.isBlank()) continue

                    // Special exception: preserve 'v' parameter (YouTube / Facebook Watch)
                    val isResourceIdParam = (key == "v")
                    // Special exception: preserve YouTube video start timestamp 't'
                    val isYouTubeTimestamp = isYouTube && (key == "t")
                    // Special exception: preserve YouTube playlist 'list'
                    val isYouTubePlaylist = isYouTube && (key == "list")

                    if (isResourceIdParam || isYouTubeTimestamp || isYouTubePlaylist) {
                        keptParams.add(param)
                    } else if (key !in TRACKING_PARAMS && !key.startsWith("utm_")) {
                        // Keep other non-tracking parameters
                        keptParams.add(param)
                    }
                }

                if (keptParams.isNotEmpty()) keptParams.joinToString("&") else null
            } else {
                null
            }

            // 4. Construct normalized URL string
            buildString {
                append(scheme).append("://").append(host)
                if (path.isNotEmpty()) {
                    append(path)
                }
                if (!cleanedQuery.isNullOrEmpty()) {
                    append("?").append(cleanedQuery)
                }
            }
        } catch (e: Exception) {
            // Fallback: strip basic known query params using string manipulation if URI parsing fails
            cleanUrlFallback(rawUrl)
        }
    }

    private fun cleanUrlFallback(url: String): String {
        val base = url.substringBefore("?")
        val query = url.substringAfter("?", "")
        if (query.isEmpty()) return base

        val kept = query.split("&").filter { param ->
            val key = param.substringBefore("=").lowercase()
            key !in TRACKING_PARAMS && !key.startsWith("utm_")
        }

        return if (kept.isNotEmpty()) "$base?${kept.joinToString("&")}" else base
    }
}

