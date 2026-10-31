package com.videapp.mobile.core.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thread-safe preferences manager for VideAPP settings:
 * - Auto-delete after sharing (5 min < 50MB, 10 min >= 50MB)
 * - FFmpeg video compression (H.264 CRF 28 720p limit)
 */
class AppPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _autoDeleteAfterShare = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_DELETE_AFTER_SHARE, false)
    )
    val autoDeleteAfterShare: StateFlow<Boolean> = _autoDeleteAfterShare.asStateFlow()

    private val _compressVideo = MutableStateFlow(
        prefs.getBoolean(KEY_COMPRESS_VIDEO, false)
    )
    val compressVideo: StateFlow<Boolean> = _compressVideo.asStateFlow()

    fun setAutoDeleteAfterShare(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_DELETE_AFTER_SHARE, enabled).apply()
        _autoDeleteAfterShare.value = enabled
    }

    fun setCompressVideo(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_COMPRESS_VIDEO, enabled).apply()
        _compressVideo.value = enabled
    }

    companion object {
        private const val PREFS_NAME = "videapp_preferences"
        private const val KEY_AUTO_DELETE_AFTER_SHARE = "key_auto_delete_after_share"
        private const val KEY_COMPRESS_VIDEO = "key_compress_video"

        @Volatile
        private var instance: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return instance ?: synchronized(this) {
                instance ?: AppPreferences(context).also { instance = it }
            }
        }
    }
}

