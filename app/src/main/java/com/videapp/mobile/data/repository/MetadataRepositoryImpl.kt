package com.videapp.mobile.data.repository

import com.videapp.mobile.core.sanitizer.UrlSanitizer
import com.videapp.mobile.domain.model.FormatOption
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

    override suspend fun extractMetadata(url: String): Result<VideoMetadata> = withContext(Dispatchers.IO) {
        try {
            val sanitizedUrl = UrlSanitizer.sanitize(url) ?: url
            val platform = UrlSanitizer.identifyPlatform(sanitizedUrl)

            val request = YoutubeDLRequest(sanitizedUrl).apply {
                addOption("--no-playlist")
                addOption("--socket-timeout", "15")
            }

            val rawInfo: VideoInfo = YoutubeDL.getInstance().getInfo(request)

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
            msg.contains("unsupported url") ->
                "Enlace no soportado o formato de URL no reconocido."
            msg.contains("bot") || msg.contains("captcha") ->
                "La plataforma bloqueó temporalmente la consulta (verificación anti-bot)."
            else ->
                "Error del motor al procesar el enlace: ${e.message}"
        }
    }
}
