package com.videapp.mobile.presentation.main

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videapp.mobile.data.repository.MediaRepositoryImpl
import com.videapp.mobile.domain.model.DownloadedMedia
import com.videapp.mobile.domain.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel orchestrating the Media Library state and ExoPlayer target selection.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val mediaRepository: MediaRepository = MediaRepositoryImpl(application.applicationContext)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        observeVideos()
        loadVideos()
    }

    private fun observeVideos() {
        viewModelScope.launch {
            mediaRepository.getDownloadedVideos().collect { list ->
                _uiState.value = _uiState.value.copy(
                    videos = list,
                    isLoading = false
                )
            }
        }
    }

    fun loadVideos() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val videos = mediaRepository.refreshVideos()
                _uiState.value = _uiState.value.copy(
                    videos = videos,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error al consultar videos: ${e.message}"
                )
            }
        }
    }

    fun selectVideo(media: DownloadedMedia?) {
        _uiState.value = _uiState.value.copy(selectedVideo = media)
    }

    fun selectVideoByUri(targetUriString: String) {
        viewModelScope.launch {
            val currentList = _uiState.value.videos
            val found = currentList.find { it.contentUri.toString() == targetUriString }
            if (found != null) {
                _uiState.value = _uiState.value.copy(selectedVideo = found)
            } else {
                // If not found yet (e.g. freshly downloaded and launched directly), refresh first
                val refreshed = mediaRepository.refreshVideos()
                val newlyFound = refreshed.find { it.contentUri.toString() == targetUriString }
                    ?: DownloadedMedia(
                        id = 0,
                        contentUri = Uri.parse(targetUriString),
                        displayName = "Video descargado",
                        sizeBytes = 0,
                        sizeFormatted = "",
                        durationMs = 0,
                        durationFormatted = "",
                        dateAddedSec = System.currentTimeMillis() / 1000,
                        mimeType = "video/mp4"
                    )
                _uiState.value = _uiState.value.copy(
                    videos = refreshed,
                    selectedVideo = newlyFound
                )
            }
        }
    }

    fun toggleSelection(uri: Uri) {
        val currentSet = _uiState.value.selectedUris.toMutableSet()
        if (currentSet.contains(uri)) {
            currentSet.remove(uri)
        } else {
            currentSet.add(uri)
        }
        _uiState.value = _uiState.value.copy(
            selectedUris = currentSet,
            isSelectionMode = currentSet.isNotEmpty()
        )
    }

    fun selectAll() {
        val allUris = _uiState.value.videos.map { it.contentUri }.toSet()
        _uiState.value = _uiState.value.copy(
            selectedUris = allUris,
            isSelectionMode = allUris.isNotEmpty()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedUris = emptySet(),
            isSelectionMode = false
        )
    }

    fun onVideosDeleted(uris: Collection<Uri>) {
        clearSelection()
        loadVideos()
    }

    fun deleteVideos(uris: Collection<Uri>) {
        viewModelScope.launch {
            mediaRepository.deleteVideos(uris.toList())
            clearSelection()
            loadVideos()
        }
    }

    fun deleteVideo(media: DownloadedMedia) {
        viewModelScope.launch {
            val success = mediaRepository.deleteVideo(media.contentUri)
            if (success) {
                if (_uiState.value.selectedVideo?.contentUri == media.contentUri) {
                    _uiState.value = _uiState.value.copy(selectedVideo = null)
                }
                loadVideos()
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "No se pudo eliminar el archivo de la biblioteca."
                )
            }
        }
    }
}

