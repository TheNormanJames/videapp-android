package com.videapp.mobile.core.worker

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * WorkManager CoroutineWorker for delayed media file deletion after user shares.
 * Adaptive delay: 5 minutes if < 50MB, 10 minutes if >= 50MB.
 */
class DelayedDeleteWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DelayedDeleteWorker"
        const val KEY_URI = "extra_target_uri"
        const val KEY_FILE_SIZE = "extra_file_size"

        private const val FIFTY_MB_BYTES = 50L * 1024L * 1024L

        /**
         * Adaptive delay rule:
         * 5 minutes if file size is below 50 MB, 10 minutes if 50 MB or larger.
         */
        fun calculateDelayMinutes(fileSizeBytes: Long): Long {
            return if (fileSizeBytes in 1 until FIFTY_MB_BYTES) 5L else 10L
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uriString = inputData.getString(KEY_URI)
        if (uriString.isNullOrBlank()) {
            Log.e(TAG, "Delayed deletion aborted: missing URI string")
            return@withContext Result.failure()
        }

        val uri = Uri.parse(uriString)
        Log.i(TAG, "Executing scheduled delayed deletion for: $uri")

        try {
            val rowsDeleted = applicationContext.contentResolver.delete(uri, null, null)
            if (rowsDeleted > 0) {
                Log.i(TAG, "Successfully deleted $rowsDeleted MediaStore entry for: $uri")
                Result.success()
            } else {
                Log.w(TAG, "No rows deleted for: $uri (File may have already been removed)")
                Result.success()
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Scoped storage security restriction on deleting $uri: ${e.message}")
            Result.failure()
        } catch (e: Throwable) {
            Log.e(TAG, "Error executing delayed deletion on $uri: ${e.message}", e)
            Result.failure()
        }
    }
}

/**
 * Scheduler helper for enqueuing delayed deletions with WorkManager.
 */
object DelayedDeleteScheduler {

    private const val TAG = "DelayedDeleteScheduler"

    /**
     * Schedules delayed deletion for the given [uri].
     * Dynamically resolves file size if not provided to compute the adaptive delay (5m vs 10m).
     */
    fun scheduleDelayedDeletion(
        context: Context,
        uri: Uri,
        knownSizeBytes: Long = 0L
    ) {
        val fileSizeBytes = if (knownSizeBytes > 0L) {
            knownSizeBytes
        } else {
            resolveFileSize(context, uri)
        }

        val delayMinutes = DelayedDeleteWorker.calculateDelayMinutes(fileSizeBytes)

        val inputData = workDataOf(
            DelayedDeleteWorker.KEY_URI to uri.toString(),
            DelayedDeleteWorker.KEY_FILE_SIZE to fileSizeBytes
        )

        val deleteRequest = OneTimeWorkRequestBuilder<DelayedDeleteWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setInputData(inputData)
            .addTag("delayed_delete_${uri.hashCode()}")
            .build()

        WorkManager.getInstance(context).enqueue(deleteRequest)
        Log.i(TAG, "Scheduled delayed deletion for $uri in $delayMinutes minutes (size: $fileSizeBytes bytes)")
    }

    private fun resolveFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
                } else 0L
            } ?: 0L
        } catch (e: Throwable) {
            Log.w(TAG, "Unable to resolve file size for $uri: ${e.message}")
            0L
        }
    }
}

