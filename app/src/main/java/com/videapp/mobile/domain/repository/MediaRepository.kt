package com.videapp.mobile.domain.repository

import android.net.Uri
import com.videapp.mobile.domain.model.DownloadedMedia
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun getDownloadedVideos(): Flow<List<DownloadedMedia>>
    suspend fun refreshVideos(): List<DownloadedMedia>
    suspend fun deleteVideo(uri: Uri): Boolean
    suspend fun deleteVideos(uris: List<Uri>): Int
}
