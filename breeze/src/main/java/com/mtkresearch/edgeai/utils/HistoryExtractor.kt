package com.mtkresearch.breeze.edgeai.utils

import org.signal.core.util.logging.Log
import com.mtkresearch.breeze.api.BreezeRegistry
import com.mtkresearch.breeze.edgeai.models.ConversationHistory
import com.mtkresearch.breeze.edgeai.models.HistoryMessage
import com.mtkresearch.breeze.edgeai.models.TransformedMessage

/**
 * Utility to extract conversation history using the BreezeDataProvider interface.
 * Used by EdgeAI usecases to provide conversation context.
 */
object HistoryExtractor {
  private val TAG = Log.tag(HistoryExtractor::class.java)
  
  /**
   * Get recent conversation history via the data provider.
   * 
   * @param threadId The thread ID to extract history from
   * @param limit Maximum number of messages to retrieve
   * @return ConversationHistory with recent messages
   */
  fun extractHistory(threadId: Long, limit: Int = 10): ConversationHistory {
    return try {
      val messages = getRecentMessages(threadId, limit)
      ConversationHistory(messages, threadId)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to extract conversation history", e)
      ConversationHistory(emptyList(), threadId)
    }
  }
  
  /**
   * Get recent messages via BreezeDataProvider interface.
   */
  fun getRecentMessages(threadId: Long, limit: Int = 10): List<HistoryMessage> {
    return try {
      val dataProvider = BreezeRegistry.dataProvider
      if (dataProvider == null) {
        Log.w(TAG, "BreezeDataProvider not registered, cannot load history")
        return emptyList()
      }
      
      // Use the data provider interface to get messages
      val messageSummaries = dataProvider.getRecentMessages(threadId, limit)
      
      // Convert to HistoryMessage format
      messageSummaries.map { summary ->
        // Extract raw text if this is a JSON message, otherwise use body as-is
        val rawText = extractRawText(summary.body ?: "")
        HistoryMessage(
          sender = summary.sender ?: "unknown",
          text = rawText,
          timestamp = 0L  // Don't expose timestamps for privacy
        )
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to load conversation history", e)
      emptyList()
    }
  }
  
  /**
   * Extract raw text from a message, handling both JSON and plain text.
   * If the message is already in our JSON format, extract just the text field.
   */
  private fun extractRawText(message: String): String {
    return try {
      // Try to parse as JSON to extract the "text" field
      val transformedMessage = TransformedMessage.fromJson(message)
      transformedMessage.text
    } catch (e: Exception) {
      // Not JSON format, return as-is
      message
    }
  }
}