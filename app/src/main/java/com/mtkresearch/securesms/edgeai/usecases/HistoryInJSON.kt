package com.mtkresearch.securesms.edgeai.usecases

import org.signal.core.util.logging.Log
import com.mtkresearch.securesms.edgeai.utils.HistoryExtractor
import com.mtkresearch.securesms.edgeai.models.TransformedMessage

/**
 * "Make History in JSON" usecase.
 * 
 * This mock AI feature transforms user input by including conversation history
 * in JSON format. The JSON output simulates how an AI would process messages
 * with full conversation context.
 * 
 * Example output:
 * {
 *   "dialog_history": [
 *     {"sender": "user123", "text": "What time works?"},
 *     {"sender": "user456", "text": "I'm free afternoon"}
 *   ],
 *   "text": "Let's meet at 3pm"
 * }
 */
object HistoryInJSON {
  private val TAG = Log.tag(HistoryInJSON::class.java)
  
  /**
   * Execute the "Make History in JSON" transformation.
   * 
   * @param request Request containing input text and thread ID
   * @return JSON string with conversation history and user input
   */
  fun execute(request: Request): String {
    return try {
      Log.d(TAG, "[HistoryInJSON] Transforming text for thread ${request.threadId}")
      
      // Extract conversation history
      val history = HistoryExtractor.extractHistory(request.threadId, request.historyLimit)
      
      // Create JSON transformation
      val transformed = TransformedMessage(
        dialogHistory = history.messages,
        text = request.inputText
      )
      
      val jsonOutput = transformed.toJson()
      Log.d(TAG, "[HistoryInJSON] Generated JSON with ${history.messages.size} history messages")
      
      jsonOutput
    } catch (e: Exception) {
      Log.e(TAG, "[HistoryInJSON] Failed to transform text", e)
      // Fallback to plain text on error
      request.inputText
    }
  }
  
  /**
   * Request data for HistoryInJSON usecase
   */
  data class Request(
    val inputText: String,
    val threadId: Long,
    val historyLimit: Int = 10
  )
}