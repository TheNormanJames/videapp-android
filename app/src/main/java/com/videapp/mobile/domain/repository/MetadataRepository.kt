package com.videapp.mobile.domain.repository

import android.content.Context
import com.videapp.mobile.domain.model.VideoMetadata

/**
 * Repository interface for extracting video metadata asynchronously.
 */
interface MetadataRepository {
    /**
     * Extracts rich metadata for a given URL without downloading the full stream.
     * Guaranteed to return a safe [Result] without crashing.
     */
    suspend fun extractMetadata(url: String): Result<VideoMetadata>

    /**
     * Defensively checks and ensures engine initialization for cold starts.
     */
    suspend fun ensureEngineReady(context: Context? = null)
}

