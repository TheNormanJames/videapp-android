package com.videapp.mobile.presentation.main

import com.videapp.mobile.domain.model.DownloadedMedia

/**
 * UI State for the Main Library Screen.
 */
data class MainUiState(
    val isLoading: Boolean = true,
    val videos: List<DownloadedMedia> = emptyList(),
    val selectedVideo: DownloadedMedia? = null,
    val errorMessage: String? = null
)

