package com.videapp.mobile.domain.model

/**
 * Immutable domain representation of video metadata extracted from social media platforms.
 */
data class VideoMetadata(
    val id: String,
    val title: String,
    val uploader: String?,
    val durationSeconds: Long,
    val thumbnailUrl: String?,
    val sourceUrl: String,
    val platform: SocialPlatform,
    val formatOptions: List<FormatOption> = emptyList()
)

data class FormatOption(
    val formatId: String,
    val label: String,               // e.g. "1080p (MP4)", "720p", "Audio (M4A)"
    val extension: String,
    val resolution: String?,         // e.g. "1920x1080"
    val approximateSizeBytes: Long = 0,
    val isVideo: Boolean = true,
    val isAudioOnly: Boolean = false
)
