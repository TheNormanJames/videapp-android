package com.videapp.mobile.domain.repository

import com.videapp.mobile.domain.model.DownloadTask
import com.videapp.mobile.domain.model.VideoInfo
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {

    companion object {
        const val DEFAULT_FORMAT_720P = "bestvideo[height<=720]+bestaudio/best[height<=720]/best"
        const val DEFAULT_SORT_720P = "res:720,fps:30,vcodec:h264"

        /**
         * Applies default 720p resolution and H.264 codec constraints to a YoutubeDLRequest.
         */
        fun applyDefaultResolutionConstraints(request: YoutubeDLRequest, formatId: String? = null) {
            if (!formatId.isNullOrBlank()) {
                request.addOption("-f", formatId)
            } else {
                request.addOption("-f", DEFAULT_FORMAT_720P)
            }
            request.addOption("-S", DEFAULT_SORT_720P)
        }
    }

    suspend fun fetchVideoInfo(url: String): Result<VideoInfo>
    suspend fun startDownload(url: String, formatId: String?): Flow<DownloadTask>
    suspend fun cancelDownload(taskId: String)
}

