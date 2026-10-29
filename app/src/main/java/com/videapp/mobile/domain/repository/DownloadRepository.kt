package com.videapp.mobile.domain.repository

import com.videapp.mobile.domain.model.DownloadTask
import com.videapp.mobile.domain.model.VideoInfo
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    suspend fun fetchVideoInfo(url: String): Result<VideoInfo>
    suspend fun startDownload(url: String, formatId: String?): Flow<DownloadTask>
    suspend fun cancelDownload(taskId: String)
}

