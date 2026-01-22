package com.mtkresearch.breeze

import android.content.Context
import android.net.Uri
import org.signal.core.util.logging.Log
import com.mtkresearch.breezeapp.edgeai.ASRRequest

/**
 * Client for interacting with the EdgeAI engine (Stub for ASR feature).
 */
object EdgeAIClient {
    private val TAG = Log.tag(EdgeAIClient::class.java)
    private var isInitialized = false

    fun initialize(context: Context): Boolean {
        isInitialized = true
        return true
    }

    fun isReady(): Boolean = isInitialized

    fun chat(prompt: String, systemPrompt: String): Result<String> {
        return Result.success("EdgeAI Chat Stub")
    }

    /**
     * Performs ASR on the given audio file.
     * 
     * Signal Audio Format: Signal records voice notes as AAC (.m4a) files (Confirmed in AudioRecorder.java, line 93: .withMimeType(MediaUtil.AUDIO_AAC)).
     * EdgeAI SDK Requirement: ASRRequest requires a raw specific 'ByteArray' of the audio data and a 'model' identifier.
     * Difference: 'Uri' is just a reference/address to the file. 'ByteArray' is the actual binary content of the file.
     * 
     * Therefor, we need 'Context' to resolve the 'Uri' and read the file content into a 'ByteArray'.
     */
    fun asr(context: Context, audioUri: Uri): Result<String> {
        Log.d(TAG, "Fake ASR requested for URI: $audioUri")
        
        try {
            // TODO: Read the file content from the Uri
            // val rawBytes = context.contentResolver.openInputStream(audioUri)?.use { it.readBytes() }
            
            // TODO: validate rawBytes

            // TODO: Construct ASRRequest with the required ByteArray and Model Name
            // val request = ASRRequest(rawBytes ?: ByteArray(0), model = "whisper-large-v3") 
            
            // Log.d(TAG, "Created ASRRequest with ${request.audio.size} bytes") // 'audio' field name might differ, safer to just log creation
            Log.d(TAG, "Created ASRRequest. Ready to send to engine.")

            // TODO: Call EdgeAI.asr(request) when ready.
            // For now, return mock response.
            return Result.success("這是語音轉文字的測試內容 (Fake ASR)")
        } catch (e: Exception) {
            Log.e(TAG, "ASR preprocessing failed", e)
            return Result.failure(e)
        }
    }
}
