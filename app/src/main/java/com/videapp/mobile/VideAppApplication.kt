package com.videapp.mobile

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Custom Application class for VideAPP.
 * Initializes the youtubedl-android engine and FFmpeg on a background thread
 * to prevent blocking the main thread during app launch.
 */
class VideAppApplication : Application(), ImageLoaderFactory {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    companion object {
        private const val TAG = "VideAppApplication"
        const val DOWNLOAD_CHANNEL_ID = "videapp_downloads_channel"

        lateinit var instance: VideAppApplication
            private set

        private val _isEngineReady = MutableStateFlow(false)
        val isEngineReady: StateFlow<Boolean> = _isEngineReady.asStateFlow()

        private val _engineInitError = MutableStateFlow<String?>(null)
        val engineInitError: StateFlow<String?> = _engineInitError.asStateFlow()

        private val initMutex = Mutex()

        /**
         * Defensive, idempotent engine initialization.
         * Ensures YoutubeDL and FFmpeg are initialized before metadata extraction or download,
         * resolving cold-start race conditions when triggered from ShareReceiverActivity.
         */
        suspend fun ensureEngineInitialized(context: Context? = null) {
            if (_isEngineReady.value) return
            initMutex.withLock {
                if (_isEngineReady.value) return@withLock
                val ctx = context?.applicationContext ?: instance.applicationContext
                try {
                    Log.d(TAG, "Idempotent initialization of YoutubeDL and FFmpeg engines...")
                    YoutubeDL.getInstance().init(ctx)
                    FFmpeg.getInstance().init(ctx)
                    _isEngineReady.value = true
                    _engineInitError.value = null
                    Log.i(TAG, "YoutubeDL and FFmpeg successfully initialized.")
                } catch (e: YoutubeDLException) {
                    val errorMsg = "Error initializing youtubedl-android: ${e.message}"
                    Log.e(TAG, errorMsg, e)
                    _engineInitError.value = errorMsg
                    throw e
                } catch (e: Throwable) {
                    val errorMsg = "Unexpected error initializing engine: ${e.message}"
                    Log.e(TAG, errorMsg, e)
                    _engineInitError.value = errorMsg
                    throw e
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        initYoutubeDLEngine()
    }

    /**
     * Initializes YoutubeDL and FFmpeg asynchronously in a background IO dispatcher.
     */
    private fun initYoutubeDLEngine() {
        applicationScope.launch {
            try {
                ensureEngineInitialized(this@VideAppApplication)

                // Auto-update yt-dlp binary asynchronously to prevent 403 Forbidden / SABR streaming issues
                try {
                    Log.d(TAG, "Checking for yt-dlp updates (Channel: STABLE)...")
                    val status = YoutubeDL.getInstance().updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.STABLE)
                    Log.i(TAG, "yt-dlp binary update status: $status (Version: ${YoutubeDL.getInstance().version(applicationContext)})")
                } catch (e: Throwable) {
                    Log.w(TAG, "yt-dlp auto-update skipped or failed: ${e.message}")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Engine background init encountered issue: ${e.message}")
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

