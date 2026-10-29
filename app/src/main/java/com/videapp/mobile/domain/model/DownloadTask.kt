package com.videapp.mobile.domain.model

data class DownloadTask(
    val taskId: String,
    val url: String,
    val title: String,
    val progress: Float = 0f,
    val speed: String = "",
    val eta: String = "",
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val destinationPath: String? = null,
    val errorMessage: String? = null
)

enum class DownloadStatus {
    QUEUED,
    FETCHING_INFO,
    DOWNLOADING,
    PROCESSING_FFMPEG,
    COMPLETED,
    FAILED,
    CANCELLED
}

