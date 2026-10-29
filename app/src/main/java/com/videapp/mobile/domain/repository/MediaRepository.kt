package com.videapp.mobile.domain.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow

data class MediaItem(
    val id: Long,
    val title: String,
    val contentUri: Uri,
    val size: Long,
    val durationMs: Long,
    val dateAdded: Long,
    val mimeType: String
)

interface MediaRepository {
    fun getDownloadedVideos(): Flow<List<MediaItem>>
    suspend fun deleteVideo(uri: Uri): Boolean
}

