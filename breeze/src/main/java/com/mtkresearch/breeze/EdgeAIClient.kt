package com.mtkresearch.breeze

import android.content.Context
import com.mtkresearch.breezeapp.edgeai.EdgeAI
import com.mtkresearch.breezeapp.edgeai.chatRequest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.signal.core.util.logging.Log
import com.mtkresearch.breezeapp.edgeai.ASRRequest
import android.net.Uri
import java.io.IOException


/**
 * Client wrapper for EdgeAI SDK.
 * Provides simplified API for chat requests with error handling.
 */
object EdgeAIClient {
    
    private val TAG = Log.tag(EdgeAIClient::class.java)
    private var isInitialized = false
    private var appContext: Context? = null
    
    /**
     * Initialize connection to BreezeApp-engine.
     * @return true if initialization successful, false otherwise
     */
    suspend fun initialize(context: Context): Boolean {
        this.appContext = context.applicationContext

        if (isInitialized && EdgeAI.isInitialized()) {
            return true
        }
        
        return try {
            Log.i(TAG, "Initializing EdgeAI connection...")
            EdgeAI.initializeAndWait(context, null, BreezeConfig.EDGEAI_INIT_TIMEOUT_MS)
            
            if (EdgeAI.isInitialized()) {
                Log.i(TAG, "EdgeAI initialized successfully")
                isInitialized = true
                true
            } else {
                Log.w(TAG, "EdgeAI initialization check returned false")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize EdgeAI", e)
            isInitialized = false
            false
        }
    }
    
    private suspend fun ensureConnection(context: Context? = null): Boolean {
        if (isReady()) return true
        
        // Use provided context or fallback to cached appContext
        val targetContext = context ?: appContext
        
        if (targetContext != null) {
            Log.i(TAG, "EdgeAI not ready, attempting auto-reconnect...")
            return initialize(targetContext)
        }
        
        return false
    }

    /**
     * Send chat request to EdgeAI (non-streaming, collects full response).
     * @param prompt The user's input message
     * @param systemPrompt The system instructions (including history context)
     * @return Complete response text, or error message if failed
     */
    suspend fun chat(prompt: String, systemPrompt: String): Result<String> {
        if (!ensureConnection()) {
            return Result.failure(IllegalStateException("EdgeAI not initialized"))
        }
        
        return try {
            Log.d(TAG, "Sending chat request. Prompt len: ${prompt.length}, System len: ${systemPrompt.length}")
            
            val request = chatRequest(
                prompt = prompt,
                systemPrompt = systemPrompt
            )
            
            // Collect all streaming chunks into single response
            val responseBuilder = StringBuilder()
            EdgeAI.chat(request).collect { response ->
                val content = response.choices.firstOrNull()?.message?.content
                if (content != null) {
                    responseBuilder.append(content)
                }
            }
            
            val fullResponse = responseBuilder.toString()
            if (fullResponse.isNotEmpty()) {
                Log.d(TAG, "Received response: ${fullResponse.take(100)}...")
                Result.success(fullResponse)
            } else {
                Result.failure(RuntimeException("Empty response from EdgeAI"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Chat request failed", e)
            Result.failure(e)
        }
    }

    /**
     * Performs ASR on the given audio bytes.
     * @param audioBytes The raw audio data
     */
    suspend fun asr(audioBytes: ByteArray): Result<String> {
        if (!ensureConnection()) {
             return Result.failure(IllegalStateException("EdgeAI not initialized"))
        }

        try {
            if (audioBytes.isEmpty()) {
                return Result.failure(IOException("Audio bytes are empty"))
            }

            // 3. Construct Request
            val request = ASRRequest(audioBytes, model="taigi")

            Log.d(TAG, "Created ASRRequest with ${audioBytes.size} bytes. Sending to engine...")

            // 4. Call Engine
            val responses = EdgeAI.asr(request).toList()

            // Real Response
            val transcription = responses.lastOrNull()?.text ?: ""
            return Result.success(transcription)
            
        } catch (e: Exception) {
            Log.e(TAG, "ASR request failed", e)
            return Result.failure(e)
        }
    }
    
    /**
     * Check if EdgeAI is ready to accept requests.
     */
    fun isReady(): Boolean = isInitialized && EdgeAI.isInitialized()
    
    /**
     * Shutdown EdgeAI connection.
     */
    fun shutdown() {
        try {
            EdgeAI.shutdown()
            isInitialized = false
            appContext = null
            Log.i(TAG, "EdgeAI shutdown complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error during EdgeAI shutdown", e)
        }
    }
}
