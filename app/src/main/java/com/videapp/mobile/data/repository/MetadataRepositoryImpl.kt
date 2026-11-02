package com.videapp.mobile.data.repository

import android.content.Context
import android.util.Log
import com.videapp.mobile.VideAppApplication
import com.videapp.mobile.core.sanitizer.UrlSanitizer
import com.videapp.mobile.domain.model.FormatOption
import com.videapp.mobile.domain.model.SocialPlatform
import com.videapp.mobile.domain.model.VideoMetadata
import com.videapp.mobile.domain.repository.MetadataRepository
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Implementation of MetadataRepository that queries youtubedl-android in Dispatchers.IO
 * and maps results safely into immutable VideoMetadata, with strict exception handling.
 */
class MetadataRepositoryImpl : MetadataRepository {

    override suspend fun ensureEngineReady(context: Context?) {
        VideAppApplication.ensureEngineInitialized(context)
    }

    override suspend fun extractMetadata(url: String): Result<VideoMetadata> = withContext(Dispatchers.IO) {
        try {
            // Defensive idempotent engine initialization for cold starts
            VideAppApplication.ensureEngineInitialized()

            val sanitizedUrl = UrlSanitizer.sanitize(url) ?: url
            val platform = UrlSanitizer.identifyPlatform(sanitizedUrl)
            val isTwitter = platform == SocialPlatform.TWITTER_X

            fun buildRequest(twitterApiMode: String? = null): YoutubeDLRequest {
                return YoutubeDLRequest(sanitizedUrl).apply {
                    addOption("--no-playlist")
                    addOption("--socket-timeout", "20")
                    val extractorArgs = buildString {
                        append("youtube:player_client=android,web")
                        if (!twitterApiMode.isNullOrBlank()) {
                            append(";twitter:api=$twitterApiMode")
                        }
                    }
                    addOption("--extractor-args", extractorArgs)
                    addOption("--no-check-certificates")
                    addOption("--no-warnings")
                }
            }

            // Dual API extraction strategy for Twitter:
            // Attempt 1: Standard GraphQL API (extracts full resolution & threads)
            // Attempt 2: Fallback to Syndication API if GraphQL fails or reports no video
            val rawInfo: VideoInfo = if (isTwitter) {
                try {
                    YoutubeDL.getInstance().getInfo(buildRequest(twitterApiMode = null))
                } catch (e1: YoutubeDLException) {
                    val msg1 = e1.message?.lowercase() ?: ""
                    if (msg1.contains("no video") || msg1.contains("twitter") || msg1.contains("429")) {
                        Log.w("MetadataRepo", "Twitter GraphQL extraction failed ($msg1). Retrying with syndication API fallback...")
                        YoutubeDL.getInstance().getInfo(buildRequest(twitterApiMode = "syndication"))
                    } else {
                        throw e1
                    }
                }
            } else {
                YoutubeDL.getInstance().getInfo(buildRequest())
            }

            val metadata = VideoMetadata(
                id = rawInfo.id ?: System.currentTimeMillis().toString(),
                title = rawInfo.title ?: "Video sin título",
                uploader = rawInfo.uploader,
                durationSeconds = rawInfo.duration.toLong(),
                thumbnailUrl = rawInfo.thumbnail,
                sourceUrl = sanitizedUrl,
                platform = platform,
                formatOptions = mapFormats(rawInfo)
            )

            Result.success(metadata)
        } catch (e: YoutubeDLException) {
            val friendlyMessage = mapYoutubeDLException(e)
            Result.failure(Exception(friendlyMessage, e))
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("Tiempo de conexión agotado al consultar el enlace. Revisa tu red.", e))
        } catch (e: UnknownHostException) {
            Result.failure(Exception("No hay conexión a internet o el servidor no responde.", e))
        } catch (e: IOException) {
            Result.failure(Exception("Error de red al consultar el video: ${e.message}", e))
        } catch (e: Throwable) {
            Result.failure(Exception("No se pudo obtener información del video: ${e.message}", e))
        }
    }

    private fun mapFormats(rawInfo: VideoInfo): List<FormatOption> {
        val formats = rawInfo.formats ?: return emptyList()
        val options = mutableListOf<FormatOption>()

        for (f in formats) {
            val formatId = f.formatId ?: continue
            val ext = f.ext ?: "mp4"
            val width = f.width
            val height = f.height
            val resolution = if (width > 0 && height > 0) "${width}x${height}" else f.formatNote
            val vcodec = f.vcodec
            val acodec = f.acodec

            val isAudioOnly = vcodec == "none" || (vcodec == null && acodec != null && acodec != "none")
            val isVideoOnly = acodec == "none" || (acodec == null && vcodec != null && vcodec != "none")
            val isVideo = !isAudioOnly

            val approxSize = if (f.fileSize > 0) {
                f.fileSize
            } else if (f.fileSizeApproximate > 0) {
                f.fileSizeApproximate
            } else {
                0L
            }

            val label = buildString {
                if (isAudioOnly) {
                    append("Audio (${ext.uppercase()})")
                    if (f.abr > 0) append(" ${f.abr}k")
                } else {
                    if (height > 0) append("${height}p") else append(resolution ?: "Video")
                    append(" (${ext.uppercase()})")
                    if (isVideoOnly) append(" [Solo video]")
                }
            }

            options.add(
                FormatOption(
                    formatId = formatId,
                    label = label,
                    extension = ext,
                    resolution = resolution,
                    approximateSizeBytes = approxSize,
                    isVideo = isVideo,
                    isAudioOnly = isAudioOnly
                )
            )
        }

        return options
    }

    private fun mapYoutubeDLException(e: YoutubeDLException): String {
        val msg = e.message?.lowercase() ?: ""
        return when {
            msg.contains("private video") || msg.contains("is private") ->
                "El video es privado o requiere inicio de sesión en la plataforma."
            msg.contains("login required") || msg.contains("sign in") ->
                "La plataforma requiere inicio de sesión para acceder a este contenido."
            msg.contains("video unavailable") || msg.contains("not available") ->
                "El video no está disponible o ha sido eliminado."
            msg.contains("no video could be found") || msg.contains("no video") ->
                "No se localizó un video disponible en este tweet. Comprueba que la publicación contenga un video nativo y que no sea contenido privado o restringido."
            msg.contains("unsupported url") ->
                "Enlace no soportado o formato de URL no reconocido."
            msg.contains("bot") || msg.contains("captcha") ->
                "La plataforma bloqueó temporalmente la consulta (verificación anti-bot)."
            else ->
                "Error del motor al procesar el enlace: ${e.message}"
        }
    }
}
