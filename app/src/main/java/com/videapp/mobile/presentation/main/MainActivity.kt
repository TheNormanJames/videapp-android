package com.videapp.mobile.presentation.main

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.videapp.mobile.core.preferences.AppPreferences
import com.videapp.mobile.core.worker.DelayedDeleteScheduler
import com.videapp.mobile.domain.model.DownloadedMedia
import com.videapp.mobile.presentation.player.VideoPlayerDialog
import com.videapp.mobile.presentation.receiver.ShareModalBottomSheet
import com.videapp.mobile.presentation.receiver.ShareViewModel
import com.videapp.mobile.presentation.theme.AccentCyan
import com.videapp.mobile.presentation.theme.ButtonCta
import com.videapp.mobile.presentation.theme.CardContainer
import com.videapp.mobile.presentation.theme.CardContainerLight
import com.videapp.mobile.presentation.theme.ErrorRed
import com.videapp.mobile.presentation.theme.SheetBackground
import com.videapp.mobile.presentation.theme.TextPrimary
import com.videapp.mobile.presentation.theme.TextSecondary
import com.videapp.mobile.presentation.theme.VideAPPTheme
import kotlinx.coroutines.launch

/**
 * Main Activity hosting the FastDownloader Media Library, Multi-selection Gallery,
 * Scoped Storage Batch Deletion, In-app Manual URL Downloader, and ExoPlayer.
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TARGET_VIDEO_URI = "TARGET_VIDEO_URI"
    }

    private val viewModel: MainViewModel by viewModels()
    private val shareViewModel: ShareViewModel by viewModels()

    private var pendingDeleteUris: List<Uri> = emptyList()

    // Activity result launcher for Scoped Storage batch and individual delete requests (Android 11+ / API 30+)
    private val deleteRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onVideosDeleted(pendingDeleteUris)
            Toast.makeText(this, "Elemento(s) eliminado(s) de la biblioteca", Toast.LENGTH_SHORT).show()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleTargetVideoIntent(intent)

        setContent {
            VideAPPTheme {
                val uiState by viewModel.uiState.collectAsState()
                val shareUiState by shareViewModel.uiState.collectAsState()

                var showAddUrlDialog by remember { mutableStateOf(false) }
                var showDownloadSheet by remember { mutableStateOf(false) }
                var showSettingsDialog by remember { mutableStateOf(false) }

                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val scope = rememberCoroutineScope()

                MainGalleryScreen(
                    uiState = uiState,
                    onRefresh = { viewModel.loadVideos() },
                    onAddUrlClick = { showAddUrlDialog = true },
                    onOpenSettings = { showSettingsDialog = true },
                    onVideoClick = { media ->
                        if (uiState.isSelectionMode) {
                            viewModel.toggleSelection(media.contentUri)
                        } else {
                            viewModel.selectVideo(media)
                        }
                    },
                    onVideoLongClick = { media ->
                        viewModel.toggleSelection(media.contentUri)
                    },
                    onToggleSelection = { uri -> viewModel.toggleSelection(uri) },
                    onSelectAll = { viewModel.selectAll() },
                    onClearSelection = { viewModel.clearSelection() },
                    onShareSelected = { shareMultipleVideos(uiState.selectedUris.toList()) },
                    onDeleteSelected = { requestDeleteVideos(uiState.selectedUris.toList()) },
                    onClosePlayer = { viewModel.selectVideo(null) },
                    onDeleteVideo = { media -> requestDeleteVideos(listOf(media.contentUri)) }
                )

                // Settings Dialog
                if (showSettingsDialog) {
                    SettingsDialog(
                        onDismiss = { showSettingsDialog = false }
                    )
                }

                // Manual URL Input Dialog
                if (showAddUrlDialog) {
                    AddUrlDialog(
                        onDismiss = { showAddUrlDialog = false },
                        onSubmitUrl = { pastedUrl ->
                            showAddUrlDialog = false
                            shareViewModel.processSharedContent(pastedUrl)
                            showDownloadSheet = true
                        }
                    )
                }

                // In-app Modal BottomSheet for downloading the entered URL
                if (showDownloadSheet) {
                    ShareModalBottomSheet(
                        uiState = shareUiState,
                        sheetState = sheetState,
                        onDismiss = {
                            scope.launch {
                                sheetState.hide()
                                showDownloadSheet = false
                                shareViewModel.resetState()
                                viewModel.loadVideos()
                            }
                        },
                        onSelectPreset = { preset -> shareViewModel.selectPreset(preset) },
                        onStartDownload = { shareViewModel.startDownload(this@MainActivity) },
                        onCancelDownload = { shareViewModel.cancelDownload(this@MainActivity) },
                        onRetry = { shareViewModel.retry() },
                        onShareFile = { uri -> shareDownloadedFile(uri) },
                        onViewVideo = { uri ->
                            scope.launch {
                                sheetState.hide()
                                showDownloadSheet = false
                                shareViewModel.resetState()
                                viewModel.selectVideoByUri(uri.toString())
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTargetVideoIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadVideos()
    }

    private fun handleTargetVideoIntent(intent: Intent?) {
        val targetUriString = intent?.getStringExtra(EXTRA_TARGET_VIDEO_URI)
        if (!targetUriString.isNullOrBlank()) {
            viewModel.selectVideoByUri(targetUriString)
        }
    }

    /**
     * Executes single or batch deletion compatible with Scoped Storage (Android 11+ / API 30+)
     * using MediaStore.createDeleteRequest and ActivityResultContracts.StartIntentSenderForResult.
     */
    private fun requestDeleteVideos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        pendingDeleteUris = uris

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
                val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                deleteRequestLauncher.launch(intentSenderRequest)
            } catch (e: Exception) {
                // Fallback direct delete
                viewModel.deleteVideos(uris)
            }
        } else {
            viewModel.deleteVideos(uris)
        }
    }

    /**
     * Shares a single media file with ACTION_SEND and schedules delayed deletion if enabled.
     */
    private fun shareDownloadedFile(uri: Uri) {
        try {
            val mime = contentResolver.getType(uri) ?: "video/*"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Compartir video con..."))

            if (AppPreferences.getInstance(this).autoDeleteAfterShare.value) {
                DelayedDeleteScheduler.scheduleDelayedDeletion(this, uri)
                Toast.makeText(this, "Borrado diferido programado tras compartir", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al compartir archivo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shares multiple media files with ACTION_SEND_MULTIPLE and schedules delayed deletions if enabled.
     */
    private fun shareMultipleVideos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        try {
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "video/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Compartir ${uris.size} videos con..."))

            if (AppPreferences.getInstance(this).autoDeleteAfterShare.value) {
                uris.forEach { uri ->
                    DelayedDeleteScheduler.scheduleDelayedDeletion(this, uri)
                }
                Toast.makeText(this, "Borrado programado para ${uris.size} video(s)", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al compartir videos: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainGalleryScreen(
    uiState: MainUiState,
    onRefresh: () -> Unit,
    onAddUrlClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onVideoClick: (DownloadedMedia) -> Unit,
    onVideoLongClick: (DownloadedMedia) -> Unit,
    onToggleSelection: (Uri) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onShareSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClosePlayer: () -> Unit,
    onDeleteVideo: (DownloadedMedia) -> Unit
) {
    Scaffold(
        containerColor = SheetBackground,
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancelar selección",
                                tint = TextPrimary
                            )
                        }
                    },
                    title = {
                        Text(
                            text = "${uiState.selectedUris.size} seleccionados",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    actions = {
                        IconButton(onClick = onSelectAll) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = "Seleccionar todos",
                                tint = TextPrimary
                            )
                        }
                        IconButton(onClick = onShareSelected) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Compartir seleccionados",
                                tint = AccentCyan
                            )
                        }
                        IconButton(onClick = onDeleteSelected) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Eliminar seleccionados",
                                tint = ErrorRed
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SheetBackground,
                        titleContentColor = TextPrimary
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AccentCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "VideAPP",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "Biblioteca FastDownloader",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Configuración",
                                tint = TextSecondary
                            )
                        }
                        IconButton(onClick = onAddUrlClick) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "Pegar enlace",
                                tint = AccentCyan
                            )
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refrescar",
                                tint = TextSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SheetBackground,
                        titleContentColor = TextPrimary
                    )
                )
            }
        },
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = onAddUrlClick,
                    containerColor = ButtonCta,
                    contentColor = TextPrimary,
                    shape = RoundedCornerShape(16.dp),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Descargar por URL",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    text = {
                        Text(
                            text = "Pegar Enlace",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            when {
                uiState.isLoading && uiState.videos.isEmpty() -> {
                    LoadingGalleryState()
                }
                uiState.videos.isEmpty() -> {
                    EmptyGalleryState(
                        onRefresh = onRefresh,
                        onAddUrlClick = onAddUrlClick
                    )
                }
                else -> {
                    VideoGridContent(
                        videos = uiState.videos,
                        selectedUris = uiState.selectedUris,
                        isSelectionMode = uiState.isSelectionMode,
                        onVideoClick = onVideoClick,
                        onVideoLongClick = onVideoLongClick
                    )
                }
            }

            // Media3 ExoPlayer dialog
            uiState.selectedVideo?.let { selected ->
                VideoPlayerDialog(
                    media = selected,
                    onDismiss = onClosePlayer,
                    onDelete = onDeleteVideo
                )
            }
        }
    }
}

@Composable
private fun LoadingGalleryState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = AccentCyan,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Cargando biblioteca...",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun EmptyGalleryState(
    onRefresh: () -> Unit,
    onAddUrlClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(CardContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Aún no tienes videos descargados",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Pega directamente cualquier enlace de YouTube, X, Facebook o Instagram, o usa el menú nativo 'Compartir'.",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onAddUrlClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ButtonCta)
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Pegar Enlace Ahora",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(CardContainer)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refrescar biblioteca",
                    tint = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun VideoGridContent(
    videos: List<DownloadedMedia>,
    selectedUris: Set<Uri>,
    isSelectionMode: Boolean,
    onVideoClick: (DownloadedMedia) -> Unit,
    onVideoLongClick: (DownloadedMedia) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(videos, key = { it.id }) { video ->
            val isSelected = selectedUris.contains(video.contentUri)
            VideoCardItem(
                media = video,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                onClick = { onVideoClick(video) },
                onLongClick = { onVideoLongClick(video) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoCardItem(
    media: DownloadedMedia,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val borderColor = if (isSelected) AccentCyan else CardContainerLight
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CardContainer.copy(alpha = 0.9f) else CardContainer
        ),
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Column {
            // Thumbnail container 16:9
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(media.contentUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = media.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Selection checkmark or play overlay icon
                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) AccentCyan else Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Seleccionado",
                                tint = SheetBackground,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Circle,
                                contentDescription = "No seleccionado",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Duration Badge
                if (media.durationMs > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = media.durationFormatted,
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Info Column
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = media.displayName,
                    color = if (isSelected) AccentCyan else TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = media.sizeFormatted,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
