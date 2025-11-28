package com.mtkresearch.breeze.edgeai

import android.content.Context
import com.mtkresearch.breezeapp.edgeai.EdgeAI as EdgeAISDK
import com.mtkresearch.breezeapp.edgeai.ChatRequest as SDKChatRequest
import com.mtkresearch.breezeapp.edgeai.ChatMessage as SDKChatMessage
import com.mtkresearch.breezeapp.edgeai.ASRRequest as SDKASRRequest
import com.mtkresearch.breezeapp.edgeai.TTSRequest as SDKTTSRequest
import com.mtkresearch.breezeapp.edgeai.EdgeAIException as SDKEdgeAIException
import com.mtkresearch.breezeapp.edgeai.ServiceConnectionException as SDKServiceConnectionException
import com.mtkresearch.breezeapp.edgeai.InvalidInputException as SDKInvalidInputException
import com.mtkresearch.breezeapp.edgeai.ModelNotFoundException as SDKModelNotFoundException
import com.mtkresearch.breeze.edgeai.models.*
import com.mtkresearch.breeze.edgeai.usecases.HistoryInJSON
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import org.signal.core.util.logging.Log

/**
 * Main EdgeAI platform interface.
 * Wraps the EdgeAI SDK and provides a clean API for AI features.
 * 
 * Features:
 * - Streaming chat with LLM
 * - ASR (Automatic Speech Recognition) from file or microphone
 * - TTS (Text-to-Speech)
 */
object EdgeAI {
  private val TAG = Log.tag(EdgeAI::class.java)
  
  @Volatile
  private var isInitialized = false
  
  /**
   * Initialize EdgeAI SDK with application context.
   * Must be called before using any AI features.
   * 
   * @param context Application context
   * @param timeoutMs Timeout for initialization (default 10 seconds)
   * @throws EdgeAIConnectionException if initialization fails
   */
  suspend fun initialize(context: Context, timeoutMs: Long = 10000L) {
    if (isInitialized) {
      Log.d(TAG, "EdgeAI already initialized")
      return
    }
    
    try {
      Log.d(TAG, "Initializing EdgeAI SDK...")
      EdgeAISDK.initializeAndWait(context.applicationContext, timeoutMs)
      isInitialized = true
      Log.i(TAG, "EdgeAI SDK initialized successfully")
    } catch (e: SDKServiceConnectionException) {
      Log.e(TAG, "Failed to initialize EdgeAI SDK", e)
      throw EdgeAIConnectionException("Failed to connect to BreezeApp Engine: ${e.message}", e)
    } catch (e: Exception) {
      Log.e(TAG, "Unexpected error during EdgeAI initialization", e)
      throw EdgeAIConnectionException("Initialization failed: ${e.message}", e)
    }
  }
  
  /**
   * Check if EdgeAI is initialized and ready to use.
   */
  fun isReady(): Boolean = isInitialized
  
  /**
   * Streaming chat with LLM.
   * Returns a Flow that emits tokens as they are generated.
   * 
   * @param prompt User's input prompt
   * @param systemPrompt Optional system prompt to guide the AI
   * @param temperature Controls randomness (0.0 to 2.0, default 0.7)
   * @param maxTokens Maximum tokens to generate
   * @param model Model identifier (empty = engine decides)
   * @return Flow of text chunks
   */
  fun chat(
    prompt: String,
    systemPrompt: String = "You are a helpful AI assistant.",
    temperature: Float = 0.7f,
    maxTokens: Int? = null,
    model: String = ""
  ): Flow<String> {
    checkInitialized()
    
    Log.d(TAG, "Starting streaming chat: prompt='$prompt'")
    
    val messages = mutableListOf<SDKChatMessage>()
    if (systemPrompt.isNotBlank()) {
      messages.add(SDKChatMessage(role = "system", content = systemPrompt))
    }
    messages.add(SDKChatMessage(role = "user", content = prompt))
    
    val request = SDKChatRequest(
      model = model,
      messages = messages,
      temperature = temperature,
      maxCompletionTokens = maxTokens,
      stream = true
    )
    
    return EdgeAISDK.chat(request)
      .map { response ->
        // Extract text from streaming response
        response.choices.firstOrNull()?.delta?.content ?: ""
      }
      .filter { it.isNotEmpty() } // Filter out empty tokens
      .catch { e ->
        Log.e(TAG, "Chat request failed", e)
        throw mapException(e)
      }
  }
  
  /**
   * ASR (Automatic Speech Recognition).
   * Converts audio to text.
   * 
   * @param audioData Raw audio bytes
   * @param format Audio format (pcm, wav, etc.)
   * @param sampleRate Sample rate in Hz (default 16000)
   * @param language Language code (default "en")
   * @return Flow of transcription text
   */
  fun asr(
    audioData: ByteArray,
    format: String = "pcm",
    sampleRate: Int = 16000,
    language: String = "en"
  ): Flow<String> {
    checkInitialized()
    
    Log.d(TAG, "Starting ASR: format=$format, sampleRate=$sampleRate, size=${audioData.size}")
    
    // ASR request expects _file (ByteArray) and model parameters
    val request = SDKASRRequest(
      _file = audioData,
      model = language // Use language as model identifier
    )
    
    return EdgeAISDK.asr(request)
      .map { response -> response.text }
      .catch { e ->
        Log.e(TAG, "ASR request failed", e)
        throw mapException(e)
      }
  }
  
  /**
   * TTS (Text-to-Speech).
   * Converts text to speech audio.
   * Note: BreezeApp Engine handles playback directly.
   * 
   * @param text Text to convert to speech
   * @param voice Voice identifier (default "alloy")
   * @param speed Speech speed (0.5 to 2.0, default 1.0)
   * @return Flow of TTS responses
   */
  fun tts(
    text: String,
    voice: String = "alloy",
    speed: Float = 1.0f
  ): Flow<TtsResponse> {
    checkInitialized()
    
    Log.d(TAG, "Starting TTS: text='$text', voice=$voice, speed=$speed")
    
    // TTS request expects input, voice, speed, and model parameters
    val request = SDKTTSRequest(
      input = text,
      voice = voice,
      speed = speed,
      model = "" // Empty model = engine decides
    )
    
    return EdgeAISDK.tts(request)
      .map { response ->
        TtsResponse(
          audioData = response.audioData,
          format = response.format,
          isComplete = true
        )
      }
      .catch { e ->
        Log.e(TAG, "TTS request failed", e)
        throw mapException(e)
      }
  }
  
  /**
   * Execute a specific AI usecase by name.
   * Legacy method for backward compatibility.
   * 
   * @param usecaseName The name of the usecase to execute
   * @param input The input data for the usecase
   * @return The result string from the usecase
   */
  fun executeUsecase(usecaseName: String, input: Any): String {
    return try {
      Log.d(TAG, "Executing usecase: $usecaseName")
      
      when (usecaseName) {
        "history_in_json" -> {
          require(input is HistoryInJSON.Request) { "Invalid input type for history_in_json" }
          HistoryInJSON.execute(input)
        }
        else -> {
          Log.w(TAG, "Unknown usecase: $usecaseName")
          "Unknown usecase: $usecaseName"
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error executing usecase: $usecaseName", e)
      "Error: ${e.message}"
    }
  }
  
  // === Helper Methods ===
  
  private fun checkInitialized() {
    if (!isInitialized) {
      throw EdgeAIConnectionException("EdgeAI not initialized. Call EdgeAI.initialize(context) first.")
    }
  }
  
  private fun mapException(e: Throwable): EdgeAIException {
    return when (e) {
      is SDKServiceConnectionException -> EdgeAIConnectionException(e.message ?: "Service connection failed", e)
      is SDKInvalidInputException -> EdgeAIInvalidInputException(e.message ?: "Invalid input", e)
      is SDKModelNotFoundException -> EdgeAIModelNotFoundException(e.message ?: "Model not found", e)
      is SDKEdgeAIException -> EdgeAIInternalException(e.message ?: "Internal error", e)
      is EdgeAIException -> e
      else -> EdgeAIInternalException("Unexpected error: ${e.message}", e)
    }
  }
}