package com.videapp.mobile.presentation.receiver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.videapp.mobile.presentation.theme.VideAPPTheme
import kotlinx.coroutines.launch

/**
 * Transparent micro-dialog activity intercepting ACTION_SEND from social networks.
 * Hosts the OLED dark modal bottom sheet without opening a full application window.
 */
class ShareReceiverActivity : ComponentActivity() {

    private val viewModel: ShareViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            VideAPPTheme {
                val uiState by viewModel.uiState.collectAsState()
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val scope = rememberCoroutineScope()

                val dismissModal = {
                    scope.launch {
                        sheetState.hide()
                        finish()
                    }
                }

                ShareModalBottomSheet(
                    uiState = uiState,
                    sheetState = sheetState,
                    onDismiss = { finish() },
                    onSelectPreset = { preset -> viewModel.selectPreset(preset) },
                    onStartDownload = { viewModel.startDownload(this@ShareReceiverActivity) },
                    onCancelDownload = { viewModel.cancelDownload(this@ShareReceiverActivity) },
                    onRetry = { viewModel.retry() },
                    onShareFile = { uri -> shareDownloadedFile(uri) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_SEND) {
            finish()
            return
        }

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()

        viewModel.processSharedContent(sharedText)
    }

    private fun shareDownloadedFile(uri: Uri) {
        try {
            val mime = contentResolver.getType(uri) ?: "video/*"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Compartir video con..."))
        } catch (e: Exception) {
            Toast.makeText(this, "Error al compartir archivo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
