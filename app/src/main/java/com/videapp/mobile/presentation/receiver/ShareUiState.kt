package com.videapp.mobile.presentation.receiver

import android.net.Uri
import com.videapp.mobile.domain.model.SocialPlatform
import com.videapp.mobile.domain.model.VideoMetadata

enum class QualityPreset(val label: String, val formatString: String) {
    BEST("Máxima Calidad (1080p / Original)", "bestvideo+bestaudio/best"),
    FAST_720P("Calidad Equilibrada (720p HD)", "bestvideo[height<=720]+bestaudio/bestvideo[width<=720]+bestaudio/best[height<=720]/best[width<=720]/best"),
    AUDIO_ONLY("Solo Audio (MP3 / M4A)", "bestaudio/best")
}

sealed interface ShareUiState {
    data class ExtractingUrl(val rawText: String) : ShareUiState

    data class LoadingMetadata(
        val url: String,
        val platform: SocialPlatform
    ) : ShareUiState

    data class MetadataReady(
        val metadata: VideoMetadata,
        val selectedPreset: QualityPreset = QualityPreset.BEST
    ) : ShareUiState

    data class Downloading(
        val taskId: String,
        val title: String,
        val progress: Float, // 0..100
        val speed: String,
        val eta: String,
        val rawUrl: String
    ) : ShareUiState

    data class Success(
        val taskId: String,
        val title: String,
        val mediaStoreUri: Uri,
        val relativePath: String
    ) : ShareUiState

    data class Error(
        val message: String,
        val retryUrl: String?
    ) : ShareUiState
}

