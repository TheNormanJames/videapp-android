package com.videapp.mobile.core.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.videapp.mobile.R
import com.videapp.mobile.VideAppApplication
import com.videapp.mobile.core.compression.VideoCompressorMiddleware
import com.videapp.mobile.core.sanitizer.UrlSanitizer
import com.videapp.mobile.data.mediastore.MediaStoreManager
import com.videapp.mobile.domain.model.DownloadState
import com.videapp.mobile.domain.repository.DownloadRepository
import com.videapp.mobile.presentation.main.MainActivity
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Foreground Service handling background video downloads with youtubedl-android.
 * Complies with Android 14+ FOREGROUND_SERVICE_DATA_SYNC requirement.
 * Features:
 * - Persistent notification with live progress, speed and ETA.
 * - Clean process termination on cancellation.
 * - Automatic atomic persistence into MediaStore (Movies/FastDownloader).
 * - Reactive StateFlow exposure for UI layers.
 */
class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeDownloadJob: Job? = null
    private var currentProcessId: String? = null
    private var isCancelledByUser = false

    private lateinit var mediaStoreManager: MediaStoreManager
    private lateinit var notificationManager: NotificationManager

    private var lastNotificationUpdateTime = 0L

    companion object {
        private const val TAG = "DownloadService"
        const val NOTIFICATION_ID = 4001

        const val ACTION_START_DOWNLOAD = "com.videapp.mobile.action.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.videapp.mobile.action.CANCEL_DOWNLOAD"

        const val EXTRA_URL = "extra_download_url"
        const val EXTRA_TITLE = "extra_video_title"
        const val EXTRA_FORMAT_ID = "extra_format_id"
        const val EXTRA_TASK_ID = "extra_task_id"

        private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
        val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

        // Helper to trigger download from UI or ViewModels
        fun startDownload(
            context: Context,
            url: String,
            title: String = "Video",
            formatId: String? = null
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_FORMAT_ID, formatId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        // Helper to cancel download
        fun cancelDownload(context: Context, taskId: String? = null) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_TASK_ID, taskId)
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaStoreManager = MediaStoreManager(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Descargando video"
                val formatId = intent.getStringExtra(EXTRA_FORMAT_ID)
                if (!url.isNullOrBlank()) {
                    executeDownload(url, title, formatId)
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: currentProcessId
                handleCancel(taskId)
            }
        }
        return START_NOT_STICKY
    }

    private fun executeDownload(rawUrl: String, videoTitle: String, formatId: String?) {
        // Cancel any previous job
        activeDownloadJob?.cancel()

        val sanitizedUrl = UrlSanitizer.sanitize(rawUrl) ?: rawUrl
        val taskId = UUID.randomUUID().toString()
        currentProcessId = taskId
        isCancelledByUser = false

        // Temporary directory inside app's private cache
        val downloadDir = File(cacheDir, "downloads").apply { if (!exists()) mkdirs() }
        val outputTemplate = File(downloadDir, "%(title)s-$taskId.%(ext)s").absolutePath

        val initialNotification = buildProgressNotification(
            taskId = taskId,
            title = videoTitle,
            contentText = "Preparando descarga...",
            progress = 0,
            indeterminate = true
        )

        startInForeground(initialNotification)
        _downloadState.value = DownloadState.Downloading(
            taskId = taskId,
            title = videoTitle,
            progress = 0f,
            speed = "--",
            eta = "--"
        )

        activeDownloadJob = serviceScope.launch {
            try {
                // Defensive check to ensure engine is initialized on cold-start
                VideAppApplication.ensureEngineInitialized(applicationContext)

                val request = YoutubeDLRequest(sanitizedUrl).apply {
                    addOption("-o", outputTemplate)
                    addOption("--no-mtime")
                    addOption("--no-playlist")
                    addOption("--socket-timeout", "20")
                    addOption("--extractor-args", "youtube:player_client=android,web;twitter:api=syndication")
                    addOption("--no-check-certificates")
                    addOption("--no-warnings")

                    if (!formatId.isNullOrBlank()) {
                        addOption("-f", formatId)
                    } else {
                        // Default 720p resolution limit
                        addOption("-f", DownloadRepository.DEFAULT_FORMAT_720P)
                    }
                    // Sort criteria forcing max 720p, 30fps, h264
                    addOption("-S", DownloadRepository.DEFAULT_SORT_720P)
                }

                Log.d(TAG, "Starting YoutubeDL execution for task $taskId: $sanitizedUrl")

                YoutubeDL.getInstance().execute(request, taskId) { progress, etaInSeconds, line ->
                    if (isCancelledByUser) return@execute

                    val safeProgress = progress.coerceIn(0f, 100f)
                    val speed = extractSpeed(line)
                    val etaFormatted = if (etaInSeconds > 0) {
                        String.format("%02d:%02d", etaInSeconds / 60, etaInSeconds % 60)
                    } else {
                        "--:--"
                    }

                    _downloadState.value = DownloadState.Downloading(
                        taskId = taskId,
                        title = videoTitle,
                        progress = safeProgress,
                        speed = speed,
                        eta = etaFormatted
                    )

                    // Throttle notification updates (maximum twice per second to protect system UI performance)
                    val now = System.currentTimeMillis()
                    if (now - lastNotificationUpdateTime >= 500) {
                        lastNotificationUpdateTime = now
                        val statusText = "$safeProgress% • $speed • ETA $etaFormatted"
                        val progressNotification = buildProgressNotification(
                            taskId = taskId,
                            title = videoTitle,
                            contentText = statusText,
                            progress = safeProgress.toInt(),
                            indeterminate = false
                        )
                        notificationManager.notify(NOTIFICATION_ID, progressNotification)
                    }
                }

                if (isCancelledByUser) return@launch

                // Post-processing / compression / saving to MediaStore
                _downloadState.value = DownloadState.Processing(taskId, "Procesando y optimizando video...")
                val processingNotification = buildProgressNotification(
                    taskId = taskId,
                    title = videoTitle,
                    contentText = "Procesando video para almacenamiento público...",
                    progress = 100,
                    indeterminate = true
                )
                notificationManager.notify(NOTIFICATION_ID, processingNotification)

                val downloadedFiles = downloadDir.listFiles { _, name -> name.contains(taskId) }
                val targetFile = downloadedFiles?.firstOrNull()

                if (targetFile != null && targetFile.exists()) {
                    // Apply VideoCompressorMiddleware if enabled in preferences
                    val processedFile = VideoCompressorMiddleware.compressIfNeeded(
                        context = applicationContext,
                        sourceFile = targetFile
                    )

                    val cleanDisplayName = processedFile.name
                        .replace("-$taskId", "")
                        .replace("compressed_", "")
                    val extension = processedFile.extension.lowercase()
                    val mimeType = if (extension == "mp3" || extension == "m4a") "audio/$extension" else "video/$extension"

                    val mediaStoreResult = mediaStoreManager.saveVideoToMediaStore(
                        sourceFile = processedFile,
                        displayName = cleanDisplayName,
                        mimeType = mimeType
                    )

                    mediaStoreResult.onSuccess { publicUri ->
                        _downloadState.value = DownloadState.Success(
                            taskId = taskId,
                            title = videoTitle,
                            mediaStoreUri = publicUri,
                            relativePath = MediaStoreManager.RELATIVE_PATH
                        )
                        showCompletedNotification(videoTitle, publicUri)
                    }.onFailure { err ->
                        _downloadState.value = DownloadState.Error(
                            taskId = taskId,
                            message = "Error al registrar en MediaStore: ${err.message}",
                            cause = err
                        )
                        showErrorNotification("Fallo al guardar archivo", err.message ?: "")
                    }
                } else {
                    val errMsg = "No se localizó el archivo descargado en el almacenamiento temporal."
                    _downloadState.value = DownloadState.Error(taskId, errMsg)
                    showErrorNotification("Error de descarga", errMsg)
                }

            } catch (e: YoutubeDLException) {
                if (isCancelledByUser) {
                    _downloadState.value = DownloadState.Cancelled(taskId)
                } else {
                    Log.e(TAG, "YoutubeDLException during download", e)
                    _downloadState.value = DownloadState.Error(taskId, e.message ?: "Error en el motor nativo", e)
                    showErrorNotification("Descarga fallida", e.message ?: "Error del motor")
                }
            } catch (e: Throwable) {
                if (isCancelledByUser) {
                    _downloadState.value = DownloadState.Cancelled(taskId)
                } else {
                    Log.e(TAG, "General exception during download", e)
                    _downloadState.value = DownloadState.Error(taskId, e.message ?: "Error desconocido", e)
                    showErrorNotification("Error inesperado", e.message ?: "Fallo de descarga")
                }
            } finally {
                stopForeground(STOP_FOREGROUND_DETACH)
            }
        }
    }

    private fun handleCancel(taskId: String?) {
        isCancelledByUser = true
        Log.i(TAG, "Cancelling download task: $taskId")

        try {
            if (!taskId.isNullOrBlank()) {
                YoutubeDL.getInstance().destroyProcessById(taskId)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error destroying YoutubeDL process: ${e.message}")
        }

        activeDownloadJob?.cancel()

        // Clean any temp files with matching taskId
        val downloadDir = File(cacheDir, "downloads")
        downloadDir.listFiles { _, name -> name.contains(taskId ?: "") }?.forEach { it.delete() }

        _downloadState.value = DownloadState.Cancelled(taskId ?: "unknown")

        val cancelledNotification = NotificationCompat.Builder(this, VideAppApplication.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Descarga cancelada")
            .setContentText("El proceso de descarga se detuvo limpiamente.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(NOTIFICATION_ID, cancelledNotification)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildProgressNotification(
        taskId: String,
        title: String,
        contentText: String,
        progress: Int,
        indeterminate: Boolean
    ): Notification {
        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            taskId.hashCode(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, VideAppApplication.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText("FastDownloader")
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancelar",
                cancelPendingIntent
            )
            .build()
    }

    private fun showCompletedNotification(title: String, mediaUri: Uri) {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completedNotification = NotificationCompat.Builder(this, VideAppApplication.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Descarga completada")
            .setContentText("$title guardado en Galería (${MediaStoreManager.SUB_DIRECTORY})")
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID, completedNotification)
    }

    private fun showErrorNotification(title: String, message: String) {
        val errorNotification = NotificationCompat.Builder(this, VideAppApplication.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID, errorNotification)
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun extractSpeed(line: String): String {
        val speedRegex = Regex("""([0-9.]+\s*[kKmMgG]i?B/s)""")
        return speedRegex.find(line)?.value ?: "-- MB/s"
    }

    override fun onDestroy() {
        activeDownloadJob?.cancel()
        super.onDestroy()
    }
}
