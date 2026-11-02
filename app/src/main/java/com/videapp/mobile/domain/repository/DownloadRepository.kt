package com.videapp.mobile.domain.repository

import com.videapp.mobile.domain.model.DownloadTask
import com.videapp.mobile.domain.model.VideoInfo
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {

    companion object {
        const val DEFAULT_FORMAT_BEST = "bestvideo+bestaudio/best"
        // 720p format preserving both landscape (height<=720) and vertical (width<=720) aspect ratios
        const val DEFAULT_FORMAT_720P = "bestvideo[height<=720]+bestaudio/bestvideo[width<=720]+bestaudio/best[height<=720]/best[width<=720]/best"
        const val DEFAULT_SORT_QUALITY = "res,quality"

        /**
         * Applies high fidelity resolution constraints to a YoutubeDLRequest.
         */
        fun applyDefaultResolutionConstraints(request: YoutubeDLRequest, formatId: String? = null) {
            if (!formatId.isNullOrBlank()) {
                request.addOption("-f", formatId)
            } else {
                request.addOption("-f", DEFAULT_FORMAT_BEST)
            }
            request.addOption("-S", DEFAULT_SORT_QUALITY)
        }
    }

    suspend fun fetchVideoInfo(url: String): Result<VideoInfo>
    suspend fun startDownload(url: String, formatId: String?): Flow<DownloadTask>
    suspend fun cancelDownload(taskId: String)
}

