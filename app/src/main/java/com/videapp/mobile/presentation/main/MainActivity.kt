package com.videapp.mobile.presentation.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.videapp.mobile.domain.model.DownloadedMedia
import com.videapp.mobile.presentation.player.VideoPlayerDialog
import com.videapp.mobile.presentation.theme.AccentCyan
import com.videapp.mobile.presentation.theme.CardContainer
import com.videapp.mobile.presentation.theme.CardContainerLight
import com.videapp.mobile.presentation.theme.SheetBackground
import com.videapp.mobile.presentation.theme.TextPrimary
import com.videapp.mobile.presentation.theme.TextSecondary
import com.videapp.mobile.presentation.theme.VideAPPTheme

/**
 * Main Activity hosting the FastDownloader Media Library and Integrated Media3 ExoPlayer.
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TARGET_VIDEO_URI = "TARGET_VIDEO_URI"
    }

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleTargetVideoIntent(intent)

        setContent {
            VideAPPTheme {
                val uiState by viewModel.uiState.collectAsState()

                MainGalleryScreen(
                    uiState = uiState,
                    onRefresh = { viewModel.loadVideos() },
                    onVideoClick = { media -> viewModel.selectVideo(media) },
                    onClosePlayer = { viewModel.selectVideo(null) },
                    onDeleteVideo = { media -> viewModel.deleteVideo(media) }
                )
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainGalleryScreen(
    uiState: MainUiState,
    onRefresh: () -> Unit,
    onVideoClick: (DownloadedMedia) -> Unit,
    onClosePlayer: () -> Unit,
    onDeleteVideo: (DownloadedMedia) -> Unit
) {
    Scaffold(
        containerColor = SheetBackground,
        topBar = {
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
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refrescar",
                            tint = AccentCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SheetBackground,
                    titleContentColor = TextPrimary
                )
            )
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
                    EmptyGalleryState(onRefresh = onRefresh)
                }
                else -> {
                    VideoGridContent(
                        videos = uiState.videos,
                        onVideoClick = onVideoClick
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
private fun EmptyGalleryState(onRefresh: () -> Unit) {
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
                text = "Comparte cualquier enlace desde YouTube, X, Facebook o Instagram hacia VideAPP para descargar de inmediato y sin anuncios.",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

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
    onVideoClick: (DownloadedMedia) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(videos, key = { it.id }) { video ->
            VideoCardItem(
                media = video,
                onClick = { onVideoClick(video) }
            )
        }
    }
}

@Composable
private fun VideoCardItem(
    media: DownloadedMedia,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardContainer),
        border = BorderStroke(1.dp, CardContainerLight)
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

                // Play overlay icon
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
                    color = TextPrimary,
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
