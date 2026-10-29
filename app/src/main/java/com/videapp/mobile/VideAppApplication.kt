package com.videapp.mobile

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Custom Application class for VideAPP.
 * Initializes the youtubedl-android engine and FFmpeg on a background thread
 * to prevent blocking the main thread during app launch.
 */
class VideAppApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "VideAppApplication"
        const val DOWNLOAD_CHANNEL_ID = "videapp_downloads_channel"

        private val _isEngineReady = MutableStateFlow(false)
        val isEngineReady: StateFlow<Boolean> = _isEngineReady.asStateFlow()

        private val _engineInitError = MutableStateFlow<String?>(null)
        val engineInitError: StateFlow<String?> = _engineInitError.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        initYoutubeDLEngine()
    }

    /**
     * Initializes YoutubeDL and FFmpeg asynchronously in a background IO dispatcher.
     */
    private fun initYoutubeDLEngine() {
        applicationScope.launch {
            try {
                Log.d(TAG, "Initializing youtubedl-android engine in background thread...")
                YoutubeDL.getInstance().init(this@VideAppApplication)
                FFmpeg.getInstance().init(this@VideAppApplication)
                _isEngineReady.value = true
                Log.i(TAG, "youtubedl-android and FFmpeg successfully initialized.")

                // Auto-update yt-dlp binary asynchronously to prevent 403 Forbidden / SABR streaming issues
                try {
                    Log.d(TAG, "Checking for yt-dlp updates (Channel: STABLE)...")
                    val status = YoutubeDL.getInstance().updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.STABLE)
                    Log.i(TAG, "yt-dlp binary update status: $status (Version: ${YoutubeDL.getInstance().version(applicationContext)})")
                } catch (e: Throwable) {
                    Log.w(TAG, "yt-dlp auto-update skipped or failed: ${e.message}")
                }
            } catch (e: YoutubeDLException) {
                val errorMsg = "Error initializing youtubedl-android: ${e.message}"
                Log.e(TAG, errorMsg, e)
                _engineInitError.value = errorMsg
            } catch (e: Throwable) {
                val errorMsg = "Unexpected error initializing engine: ${e.message}"
                Log.e(TAG, errorMsg, e)
                _engineInitError.value = errorMsg
            }
        }
    }

    /**
     * Creates notification channels for foreground download progress.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.download_channel_desc)
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}

