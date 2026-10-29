package com.videapp.mobile.domain.model

data class VideoInfo(
    val id: String,
    val title: String,
    val url: String,
    val durationSeconds: Long = 0,
    val thumbnailUrl: String? = null,
    val uploader: String? = null,
    val sourcePlatform: SocialPlatform = SocialPlatform.UNKNOWN,
    val availableFormats: List<VideoFormat> = emptyList()
)

data class VideoFormat(
    val formatId: String,
    val ext: String,
    val resolution: String?,
    val fileSizeApprox: Long = 0,
    val isVideoOnly: Boolean = false,
    val isAudioOnly: Boolean = false
)

enum class SocialPlatform {
    YOUTUBE,
    TWITTER_X,
    INSTAGRAM,
    FACEBOOK,
    TIKTOK,
    UNKNOWN
}

