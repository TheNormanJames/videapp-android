package com.videapp.mobile.data.mediastore

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * Manages video persistence into Android's public MediaStore repository.
 * Fully compliant with Scoped Storage for Android 10, 11, 12, 13, 14, and 15+.
 * Directs files to Movies/FastDownloader without requiring legacy storage permissions.
 */
class MediaStoreManager(private val context: Context) {

    companion object {
        private const val TAG = "MediaStoreManager"
        const val SUB_DIRECTORY = "FastDownloader"
        val RELATIVE_PATH = "${Environment.DIRECTORY_MOVIES}/$SUB_DIRECTORY"
    }

    /**
     * Copies a completed local download file into MediaStore public Movies/FastDownloader collection.
     * Marks IS_PENDING = 1 during the streaming copy, and clears it to 0 when finished so
     * video players and the system gallery recognize the complete file atomically.
     */
    suspend fun saveVideoToMediaStore(
        sourceFile: File,
        displayName: String,
        mimeType: String = "video/mp4"
    ): Result<Uri> = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return@withContext Result.failure(
                IllegalArgumentException("El archivo fuente no existe o está vacío: ${sourceFile.absolutePath}")
            )
        }

        try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                put(MediaStore.Video.Media.SIZE, sourceFile.length())
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_PATH)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val collectionUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val insertedUri = resolver.insert(collectionUri, contentValues)
                ?: return@withContext Result.failure(
                    IllegalStateException("No se pudo registrar la entrada en MediaStore.")
                )

            try {
                resolver.openOutputStream(insertedUri)?.use { outputStream ->
                    FileInputStream(sourceFile).use { inputStream ->
                        inputStream.copyTo(outputStream, bufferSize = 64 * 1024)
                    }
                    outputStream.flush()
                } ?: run {
                    resolver.delete(insertedUri, null, null)
                    return@withContext Result.failure(
                        IllegalStateException("No se pudo abrir el descriptor de salida de MediaStore.")
                    )
                }

                // Atomic commit: clear pending status
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val finishValues = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    resolver.update(insertedUri, finishValues, null, null)
                }

                Log.i(TAG, "Video guardado exitosamente en MediaStore: $insertedUri")

                // Safely delete intermediate temp file
                sourceFile.delete()

                Result.success(insertedUri)
            } catch (copyError: Throwable) {
                // If streaming failed, clean up the orphaned entry in MediaStore
                resolver.delete(insertedUri, null, null)
                throw copyError
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error persistiendo video en MediaStore: ${e.message}", e)
            Result.failure(e)
        }
    }
}
