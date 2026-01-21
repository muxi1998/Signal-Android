package com.mtkresearch.breeze

import android.content.Context
import com.mtkresearch.breezeapp.edgeai.EdgeAI
import com.mtkresearch.breezeapp.edgeai.chatRequest
import kotlinx.coroutines.flow.firstOrNull
import org.signal.core.util.logging.Log

/**
 * Client wrapper for EdgeAI SDK.
 * Provides simplified API for chat requests with error handling.
 */
object EdgeAIClient {
    
    private val TAG = Log.tag(EdgeAIClient::class.java)
    private var isInitialized = false
    
    /**
     * Initialize connection to BreezeApp-engine.
     * @return true if initialization successful, false otherwise
     */
    suspend fun initialize(context: Context): Boolean {
        if (isInitialized && EdgeAI.isInitialized()) {
            return true
        }
        
        return try {
            Log.i(TAG, "Initializing EdgeAI connection...")
            EdgeAI.initializeAndWait(context, BreezeConfig.EDGEAI_INIT_TIMEOUT_MS)
            
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
    
    /**
     * Send chat request to EdgeAI (non-streaming, collects full response).
     * @param prompt The user's input message
     * @param systemPrompt The system instructions (including history context)
     * @return Complete response text, or error message if failed
     */
    suspend fun chat(prompt: String, systemPrompt: String): Result<String> {
        if (!isReady()) {
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
            Log.i(TAG, "EdgeAI shutdown complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error during EdgeAI shutdown", e)
        }
    }
}
