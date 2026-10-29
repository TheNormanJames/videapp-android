package com.videapp.mobile.domain.model

import android.net.Uri

/**
 * Domain entity representing a downloaded video item persisted in public MediaStore.
 */
data class DownloadedMedia(
    val id: Long,
    val contentUri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val durationMs: Long,
    val durationFormatted: String,
    val dateAddedSec: Long,
    val mimeType: String
)

