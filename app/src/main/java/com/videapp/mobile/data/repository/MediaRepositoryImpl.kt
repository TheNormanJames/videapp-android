package com.videapp.mobile.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.videapp.mobile.domain.model.DownloadedMedia
import com.videapp.mobile.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Implementation of MediaRepository querying MediaStore.Video for FastDownloader videos.
 * Supports reactive observation and atomic deletion.
 */
class MediaRepositoryImpl(private val context: Context) : MediaRepository {

    companion object {
        private const val TAG = "MediaRepositoryImpl"
        private const val FOLDER_NAME = "FastDownloader"
    }

    private val _videosFlow = MutableStateFlow<List<DownloadedMedia>>(emptyList())

    override fun getDownloadedVideos(): Flow<List<DownloadedMedia>> {
        return _videosFlow.asStateFlow()
    }

    override suspend fun refreshVideos(): List<DownloadedMedia> = withContext(Dispatchers.IO) {
        val videos = queryVideosInternal()
        _videosFlow.value = videos
        videos
    }

    override suspend fun deleteVideo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val deletedRows = context.contentResolver.delete(uri, null, null)
            val success = deletedRows > 0
            if (success) {
                // Instantly update flow state
                _videosFlow.value = _videosFlow.value.filter { it.contentUri != uri }
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting video from MediaStore: $uri", e)
            false
        }
    }

    private fun queryVideosInternal(): List<DownloadedMedia> {
        val items = mutableListOf<DownloadedMedia>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE
        )

        val (selection, selectionArgs) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val sel = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} = ?"
            val args = arrayOf("%$FOLDER_NAME%", FOLDER_NAME)
            Pair(sel, args)
        } else {
            val sel = "${MediaStore.Video.Media.DATA} LIKE ?"
            val args = arrayOf("%$FOLDER_NAME%")
            Pair(sel, args)
        }

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val name = cursor.getString(nameCol) ?: "Video_$id.mp4"
                    val sizeBytes = cursor.getLong(sizeCol)
                    val durationMs = cursor.getLong(durCol)
                    val dateAdded = cursor.getLong(dateCol)
                    val mimeType = cursor.getString(mimeCol) ?: "video/mp4"

                    items.add(
                        DownloadedMedia(
                            id = id,
                            contentUri = contentUri,
                            displayName = name,
                            sizeBytes = sizeBytes,
                            sizeFormatted = formatFileSize(sizeBytes),
                            durationMs = durationMs,
                            durationFormatted = formatDuration(durationMs),
                            dateAddedSec = dateAdded,
                            mimeType = mimeType
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query MediaStore for videos", e)
        }

        return items
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            val gb = mb / 1024.0
            String.format("%.2f GB", gb)
        } else {
            String.format("%.1f MB", mb)
        }
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return if (min >= 60) {
            val hours = min / 60
            val remMin = min % 60
            String.format("%d:%02d:%02d", hours, remMin, sec)
        } else {
            String.format("%02d:%02d", min, sec)
        }
    }
}
