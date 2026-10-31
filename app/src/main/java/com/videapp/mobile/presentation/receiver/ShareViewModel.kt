package com.videapp.mobile.presentation.receiver

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.videapp.mobile.core.sanitizer.UrlSanitizer
import com.videapp.mobile.core.service.DownloadService
import com.videapp.mobile.data.repository.MetadataRepositoryImpl
import com.videapp.mobile.domain.model.DownloadState
import com.videapp.mobile.domain.repository.MetadataRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel governing the reactive state machine for the Share modal bottom sheet.
 * Connects UrlSanitizer, MetadataRepository and DownloadService.
 */
class ShareViewModel(
    private val metadataRepository: MetadataRepository = MetadataRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShareUiState>(ShareUiState.ExtractingUrl(""))
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    private var currentUrl: String? = null
    private var downloadObserverJob: Job? = null

    init {
        observeDownloadService()
    }

    /**
     * Entry point when an intent with shared text arrives.
     */
    fun processSharedContent(sharedText: String?) {
        if (sharedText.isNullOrBlank()) {
            _uiState.value = ShareUiState.Error(
                message = "No se recibió ningún texto o enlace para procesar.",
                retryUrl = null
            )
            return
        }

        _uiState.value = ShareUiState.ExtractingUrl(sharedText)

        val cleanUrl = UrlSanitizer.sanitize(sharedText)
        if (cleanUrl.isNullOrBlank()) {
            _uiState.value = ShareUiState.Error(
                message = "No se detectó un enlace web compatible (YouTube, X, Facebook, Instagram) en el texto.",
                retryUrl = null
            )
            return
        }

        currentUrl = cleanUrl
        loadMetadata(cleanUrl)
    }

    private fun loadMetadata(url: String) {
        val platform = UrlSanitizer.identifyPlatform(url)
        _uiState.value = ShareUiState.LoadingMetadata(url = url, platform = platform)

        viewModelScope.launch {
            val result = metadataRepository.extractMetadata(url)
            result.onSuccess { metadata ->
                _uiState.value = ShareUiState.MetadataReady(
                    metadata = metadata,
                    selectedPreset = QualityPreset.BEST
                )
            }.onFailure { error ->
                _uiState.value = ShareUiState.Error(
                    message = error.message ?: "No se pudo extraer la información del enlace.",
                    retryUrl = url
                )
            }
        }
    }

    fun selectPreset(preset: QualityPreset) {
        val current = _uiState.value
        if (current is ShareUiState.MetadataReady) {
            _uiState.value = current.copy(selectedPreset = preset)
        }
    }

    fun startDownload(context: Context) {
        val current = _uiState.value
        if (current is ShareUiState.MetadataReady) {
            val metadata = current.metadata
            val preset = current.selectedPreset

            _uiState.value = ShareUiState.Downloading(
                taskId = "init",
                title = metadata.title,
                progress = 0f,
                speed = "--",
                eta = "Calculando...",
                rawUrl = metadata.sourceUrl
            )

            DownloadService.startDownload(
                context = context,
                url = metadata.sourceUrl,
                title = metadata.title,
                formatId = preset.formatString
            )
        }
    }

    fun cancelDownload(context: Context) {
        val current = _uiState.value
        if (current is ShareUiState.Downloading) {
            DownloadService.cancelDownload(context, current.taskId)
            _uiState.value = ShareUiState.Error(
                message = "Descarga cancelada.",
                retryUrl = current.rawUrl
            )
        }
    }

    fun retry() {
        currentUrl?.let { loadMetadata(it) }
    }

    private fun observeDownloadService() {
        downloadObserverJob?.cancel()
        downloadObserverJob = viewModelScope.launch {
            DownloadService.downloadState.collect { state ->
                when (state) {
                    is DownloadState.Downloading -> {
                        _uiState.value = ShareUiState.Downloading(
                            taskId = state.taskId,
                            title = state.title,
                            progress = state.progress,
                            speed = state.speed,
                            eta = state.eta,
                            rawUrl = currentUrl ?: ""
                        )
                    }
                    is DownloadState.Processing -> {
                        _uiState.value = ShareUiState.Downloading(
                            taskId = state.taskId,
                            title = state.taskId,
                            progress = 100f,
                            speed = "Procesando",
                            eta = state.message,
                            rawUrl = currentUrl ?: ""
                        )
                    }
                    is DownloadState.Success -> {
                        _uiState.value = ShareUiState.Success(
                            taskId = state.taskId,
                            title = state.title,
                            mediaStoreUri = state.mediaStoreUri,
                            relativePath = state.relativePath
                        )
                    }
                    is DownloadState.Cancelled -> {
                        if (_uiState.value is ShareUiState.Downloading) {
                            _uiState.value = ShareUiState.Error(
                                message = "Descarga cancelada.",
                                retryUrl = currentUrl
                            )
                        }
                    }
                    is DownloadState.Error -> {
                        if (_uiState.value is ShareUiState.Downloading) {
                            _uiState.value = ShareUiState.Error(
                                message = state.message,
                                retryUrl = currentUrl
                            )
                        }
                    }
                    is DownloadState.FetchingMetadata -> {
                        // Handled during initial metadata phase
                    }
                    DownloadState.Idle -> {
                        // Keep current UI state
                    }
                }
            }
        }
    }

    fun resetState() {
        currentUrl = null
        _uiState.value = ShareUiState.ExtractingUrl("")
    }

    override fun onCleared() {
        downloadObserverJob?.cancel()
        super.onCleared()
    }
}
