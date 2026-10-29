package com.videapp.mobile.domain.model

import android.net.Uri

/**
 * Reactive state representing the download lifecycle for UI and foreground service consumption.
 */
sealed interface DownloadState {
    /**
     * Initial or idle state when no download is active.
     */
    data object Idle : DownloadState

    /**
     * Fetching metadata/stream info from social media platform.
     */
    data class FetchingMetadata(val url: String) : DownloadState

    /**
     * Active downloading state with real-time metrics.
     */
    data class Downloading(
        val taskId: String,
        val title: String,
        val progress: Float,             // Progress percentage: 0f .. 100f
        val speed: String,               // Formatted speed, e.g. "4.2 MB/s"
        val eta: String,                 // Formatted ETA, e.g. "00:15"
        val downloadedBytes: Long = 0,
        val totalBytes: Long = 0
    ) : DownloadState

    /**
     * Post-processing state (e.g. FFmpeg audio/video muxing).
     */
    data class Processing(
        val taskId: String,
        val message: String
    ) : DownloadState

    /**
     * Successful completion, saved to public MediaStore.
     */
    data class Success(
        val taskId: String,
        val title: String,
        val mediaStoreUri: Uri,
        val relativePath: String
    ) : DownloadState

    /**
     * Download cancelled by user.
     */
    data class Cancelled(val taskId: String) : DownloadState

    /**
     * Download or extraction failed with mapped error.
     */
    data class Error(
        val taskId: String?,
        val message: String,
        val cause: Throwable? = null
    ) : DownloadState
}
