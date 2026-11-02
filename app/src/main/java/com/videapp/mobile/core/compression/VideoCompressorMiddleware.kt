package com.videapp.mobile.core.compression

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.videapp.mobile.VideAppApplication
import com.videapp.mobile.core.preferences.AppPreferences
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Extension functions for [FFmpeg] enabling execution of native ffmpeg commands
 * through ProcessBuilder using the binary provisioned by FFmpeg.init().
 */
fun FFmpeg.execute(
    context: Context,
    command: List<String>,
    onOutputLine: ((String) -> Unit)? = null
): Int {
    return try {
        val packagesDir = File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg")
        val extractedBinary = File(packagesDir, "ffmpeg")
        val nativeLibBinary = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")

        val binaryToExecute = when {
            extractedBinary.exists() -> extractedBinary
            nativeLibBinary.exists() -> nativeLibBinary
            else -> {
                Log.e("FFmpegExtension", "No FFmpeg executable found in $extractedBinary or $nativeLibBinary")
                return -1
            }
        }

        if (!binaryToExecute.canExecute()) {
            binaryToExecute.setExecutable(true, false)
        }

        val fullCommand = ArrayList<String>(command.size + 1).apply {
            add(binaryToExecute.absolutePath)
            addAll(command)
        }

        val processBuilder = ProcessBuilder(fullCommand)
        processBuilder.redirectErrorStream(true)

        val env = processBuilder.environment()
        env["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir
        env["PATH"] = "${packagesDir.absolutePath}:${context.applicationInfo.nativeLibraryDir}:${env["PATH"] ?: ""}"

        val process = processBuilder.start()

        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { onOutputLine?.invoke(it) }
            }
        }

        process.waitFor()
    } catch (e: Throwable) {
        Log.e("FFmpegExtension", "Error during FFmpeg execution: ${e.message}", e)
        -1
    }
}

fun FFmpeg.execute(
    context: Context,
    vararg args: String
): Int = execute(context, args.toList())

/**
 * Video compression middleware enforcing H.264 profile CRF 28 and -2:min(720,ih) scale.
 * Invoked before MediaStore insertion when video compression is active in app settings.
 */
object VideoCompressorMiddleware {

    private const val TAG = "VideoCompressorMiddleware"

    /**
     * Compresses [sourceFile] if enabled in [AppPreferences] (or forced via [forceCompress]).
     * Returns either the compressed file or original [sourceFile] safely.
     */
    suspend fun compressIfNeeded(
        context: Context,
        sourceFile: File,
        forceCompress: Boolean = false
    ): File = withContext(Dispatchers.IO) {
        val isEnabled = forceCompress || AppPreferences.getInstance(context).compressVideo.value
        if (!isEnabled) {
            Log.d(TAG, "Video compression is disabled in settings. Skipping.")
            return@withContext sourceFile
        }

        if (!sourceFile.exists() || sourceFile.length() <= 0L) {
            Log.w(TAG, "Source file invalid or missing. Skipping compression.")
            return@withContext sourceFile
        }

        val ext = sourceFile.extension.lowercase()
        val videoExtensions = setOf("mp4", "mkv", "webm", "mov", "avi", "3gp")
        if (ext !in videoExtensions) {
            Log.d(TAG, "File extension '.$ext' is not a video. Skipping compression.")
            return@withContext sourceFile
        }

        val tempOutputFile = File(
            sourceFile.parentFile,
            "compressed_${System.currentTimeMillis()}_${sourceFile.name}"
        )

        // Inspect video dimensions to preserve orientation and sharpness without unnecessary downscaling
        var videoWidth = 0
        var videoHeight = 0
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(sourceFile.absolutePath)
            videoWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            videoHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            retriever.release()
        } catch (e: Throwable) {
            Log.w(TAG, "Could not determine video dimensions via MediaMetadataRetriever: ${e.message}")
        }

        val ffmpegArgs = mutableListOf(
            "-y",
            "-i", sourceFile.absolutePath,
            "-c:v", "libx264",
            "-crf", "22", // High visual fidelity (replaces pixelated CRF 28)
            "-preset", "fast"
        )

        // Smart orientation-aware scale: only downscale if the short edge exceeds 720p
        if (videoWidth > 0 && videoHeight > 0) {
            val isLandscape = videoWidth >= videoHeight
            val shortSide = if (isLandscape) videoHeight else videoWidth
            if (shortSide > 720) {
                val scaleFilter = if (isLandscape) "scale=-2:720" else "scale=720:-2"
                ffmpegArgs.add("-vf")
                ffmpegArgs.add(scaleFilter)
                Log.d(TAG, "Downscaling high-res video ($videoWidth x $videoHeight) using filter: $scaleFilter")
            } else {
                Log.d(TAG, "Video resolution ($videoWidth x $videoHeight) is already <= 720p. Preserving native resolution.")
            }
        }

        ffmpegArgs.add("-c:a")
        ffmpegArgs.add("aac")
        ffmpegArgs.add("-b:a")
        ffmpegArgs.add("192k")
        ffmpegArgs.add(tempOutputFile.absolutePath)

        Log.i(TAG, "Executing high-quality FFmpeg compression: H.264 CRF 22 on ${sourceFile.name}")

        val exitCode = FFmpeg.getInstance().execute(context, ffmpegArgs) { line ->
            Log.v(TAG, "ffmpeg output: $line")
        }

        if (exitCode == 0 && tempOutputFile.exists() && tempOutputFile.length() > 0L) {
            val originalSize = sourceFile.length()
            val compressedSize = tempOutputFile.length()
            // If compression actually reduced file size, keep it; otherwise preserve original
            if (compressedSize < originalSize) {
                val reductionPercent = ((originalSize - compressedSize).toDouble() / originalSize.toDouble() * 100.0).toInt()
                Log.i(TAG, "Video compressed successfully: $originalSize -> $compressedSize bytes ($reductionPercent% reduction)")
                sourceFile.delete()
                tempOutputFile
            } else {
                Log.i(TAG, "Original file is already smaller than compressed output ($originalSize vs $compressedSize bytes). Keeping original.")
                tempOutputFile.delete()
                sourceFile
            }
        } else {
            Log.w(TAG, "FFmpeg returned code $exitCode. Keeping original uncompressed file.")
            if (tempOutputFile.exists()) {
                tempOutputFile.delete()
            }
            sourceFile
        }
    }
}

