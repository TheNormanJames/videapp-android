package com.videapp.mobile.presentation.receiver

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.videapp.mobile.domain.model.SocialPlatform
import com.videapp.mobile.domain.model.VideoMetadata
import com.videapp.mobile.presentation.theme.AccentCyan
import com.videapp.mobile.presentation.theme.ButtonCta
import com.videapp.mobile.presentation.theme.CardContainer
import com.videapp.mobile.presentation.theme.CardContainerLight
import com.videapp.mobile.presentation.theme.ErrorRed
import com.videapp.mobile.presentation.theme.SheetBackground
import com.videapp.mobile.presentation.theme.SuccessEmerald
import com.videapp.mobile.presentation.theme.TextPrimary
import com.videapp.mobile.presentation.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareModalBottomSheet(
    uiState: ShareUiState,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSelectPreset: (QualityPreset) -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetry: () -> Unit,
    onShareFile: (Uri) -> Unit,
    onViewVideo: (Uri) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(TextSecondary.copy(alpha = 0.4f))
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (uiState) {
                is ShareUiState.ExtractingUrl -> {
                    ExtractingUrlContent()
                }
                is ShareUiState.LoadingMetadata -> {
                    LoadingMetadataContent(url = uiState.url, platform = uiState.platform)
                }
                is ShareUiState.MetadataReady -> {
                    MetadataReadyContent(
                        metadata = uiState.metadata,
                        selectedPreset = uiState.selectedPreset,
                        onSelectPreset = { preset ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectPreset(preset)
                        },
                        onDownloadClicked = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStartDownload()
                        }
                    )
                }
                is ShareUiState.Downloading -> {
                    DownloadingContent(
                        state = uiState,
                        onCancelClicked = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCancelDownload()
                        }
                    )
                }
                is ShareUiState.Success -> {
                    SuccessContent(
                        state = uiState,
                        onViewClicked = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onViewVideo(uiState.mediaStoreUri)
                        },
                        onShareClicked = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onShareFile(uiState.mediaStoreUri)
                        },
                        onCloseClicked = onDismiss
                    )
                }
                is ShareUiState.Error -> {
                    ErrorContent(
                        message = uiState.message,
                        onRetryClicked = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onRetry()
                        },
                        onCloseClicked = onDismiss
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ExtractingUrlContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = AccentCyan,
            strokeWidth = 3.dp,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Validando enlace recibido...",
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun LoadingMetadataContent(url: String, platform: SocialPlatform) {
    val platformName = when (platform) {
        SocialPlatform.YOUTUBE -> "YouTube"
        SocialPlatform.TWITTER_X -> "X (Twitter)"
        SocialPlatform.FACEBOOK -> "Facebook"
        SocialPlatform.INSTAGRAM -> "Instagram"
        SocialPlatform.TIKTOK -> "TikTok"
        SocialPlatform.UNKNOWN -> "Red Social"
    }

    // Shimmer skeleton transition
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alphaAnim by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardContainerLight)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = platformName,
                    color = AccentCyan,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Obteniendo información...",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Skeleton Thumbnail 16:9
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(CardContainer.copy(alpha = alphaAnim)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = AccentCyan,
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Skeleton title lines
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(18.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CardContainer.copy(alpha = alphaAnim))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(14.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CardContainer.copy(alpha = alphaAnim))
        )
    }
}

@Composable
private fun MetadataReadyContent(
    metadata: VideoMetadata,
    selectedPreset: QualityPreset,
    onSelectPreset: (QualityPreset) -> Unit,
    onDownloadClicked: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        // Thumbnail with 16:9 ratio and rounded corners
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(CardContainer)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(metadata.thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = metadata.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth()
            )

            // Duration badge
            if (metadata.durationSeconds > 0) {
                val formattedDuration = formatDuration(metadata.durationSeconds)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formattedDuration,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Video Title (max 2 lines)
        Text(
            text = metadata.title,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 22.sp
        )

        // Uploader / Author
        metadata.uploader?.let { uploader ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = uploader,
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Horizontal Quality Selector Chips
        Text(
            text = "Formato de descarga:",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QualityPreset.entries.forEach { preset ->
                val isSelected = preset == selectedPreset
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectPreset(preset) },
                    label = {
                        Text(
                            text = preset.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = CardContainer,
                        labelColor = TextSecondary,
                        selectedContainerColor = AccentCyan.copy(alpha = 0.2f),
                        selectedLabelColor = AccentCyan
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = CardContainerLight,
                        selectedBorderColor = AccentCyan,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 1.5.dp
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Main CTA Button "Descargar Ahora"
        Button(
            onClick = onDownloadClicked,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ButtonCta)
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Descargar Ahora",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DownloadingContent(
    state: ShareUiState.Downloading,
    onCancelClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Descargando video...",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = state.title,
            color = TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Continuous Cyan LinearProgressIndicator
        LinearProgressIndicator(
            progress = { state.progress / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = AccentCyan,
            trackColor = CardContainerLight
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Telemetry Text: Percentage + Speed + ETA
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${String.format("%.1f", state.progress)}%",
                color = AccentCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = "${state.speed}  •  ETA ${state.eta}",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Cancel Text Button
        TextButton(
            onClick = onCancelClicked,
            colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
        ) {
            Text(
                text = "Cancelar Descarga",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun SuccessContent(
    state: ShareUiState.Success,
    onViewClicked: () -> Unit,
    onShareClicked: () -> Unit,
    onCloseClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + scaleIn()
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(SuccessEmerald.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Éxito",
                    tint = SuccessEmerald,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "¡Descarga Exitosa!",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = state.title,
            color = TextSecondary,
            fontSize = 13.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Guardado en Galería (${state.relativePath})",
            color = AccentCyan,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Primary Button: Ver Video
        Button(
            onClick = onViewClicked,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = SheetBackground,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Ver Video",
                color = SheetBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Secondary Button: Compartir Archivo
        Button(
            onClick = onShareClicked,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ButtonCta)
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Compartir Archivo",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tertiary Button: Cerrar
        OutlinedButton(
            onClick = onCloseClicked,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardContainerLight),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
        ) {
            Text(
                text = "Cerrar",
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetryClicked: () -> Unit,
    onCloseClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = "Error",
            tint = ErrorRed,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "No se pudo completar",
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardContainer)
                .border(1.dp, CardContainerLight, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Text(
                text = message,
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onCloseClicked,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) {
                Text("Cerrar")
            }

            Button(
                onClick = onRetryClicked,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ButtonCta)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reintentar")
            }
        }
    }
}

private fun formatDuration(durationSeconds: Long): String {
    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        val remMinutes = minutes % 60
        String.format("%d:%02d:%02d", hours, remMinutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
