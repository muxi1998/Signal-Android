package com.mtkresearch.securesms.edgeai

import org.signal.core.util.logging.Log
import com.mtkresearch.securesms.edgeai.usecases.HistoryInJSON

/**
 * Main EdgeAI platform interface.
 * A thin layer that leverages EdgeAI SDK and routes to specific usecases.
 * 
 * Future integration:
 * - .chat() for conversational AI
 * - .tts() for text-to-speech
 * - .asr() for automatic speech recognition
 */
object EdgeAI {
  private val TAG = Log.tag(EdgeAI::class.java)
  
  // Future: Real SDK integration
  // private val sdk = EdgeAISDK.getInstance()
  
  /**
   * Execute a specific AI usecase by name.
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
        // Future usecases:
        // "text_refinement" -> TextRefinement.execute(input as TextRefinementRequest)
        // "tone_transform" -> ToneTransform.execute(input as ToneTransformRequest)
        // "smart_reply" -> SmartReply.execute(input as SmartReplyRequest)
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
  
  // Future SDK methods when EdgeAI SDK is available:
  // suspend fun chat(prompt: String): String = sdk.chat(prompt)
  // suspend fun tts(text: String): ByteArray = sdk.tts(text)
  // suspend fun asr(audio: ByteArray): String = sdk.asr(audio)
}