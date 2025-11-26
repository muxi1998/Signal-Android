package com.mtkresearch.securesms.edgeai.utils

import org.signal.core.util.logging.Log
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.thoughtcrime.securesms.database.SignalDatabase
import com.mtkresearch.securesms.edgeai.models.ConversationHistory
import com.mtkresearch.securesms.edgeai.models.HistoryMessage
import com.mtkresearch.securesms.edgeai.models.TransformedMessage

/**
 * Utility to extract conversation history from Signal's database.
 * Used by EdgeAI usecases to provide conversation context.
 */
object HistoryExtractor {
  private val TAG = Log.tag(HistoryExtractor::class.java)
  
  /**
   * Get recent conversation history from Signal's database.
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
   * Get recent messages from Signal's database.
   * Extracted from MrUtil's conversation history logic.
   */
  fun getRecentMessages(threadId: Long, limit: Int = 10): List<HistoryMessage> {
    return try {
      val messages = mutableListOf<HistoryMessage>()
      
      SignalDatabase.messages.getConversation(threadId, 0, limit.toLong()).use { cursor ->
        while (cursor.moveToNext()) {
          val senderId = cursor.requireLong("from_recipient_id").toString()
          val body = cursor.requireString("body") ?: ""
          
          if (body.isNotEmpty()) {
            // Extract raw text if this is a JSON message, otherwise use body as-is
            val rawText = extractRawText(body)
            messages.add(HistoryMessage(
              sender = senderId,
              text = rawText,
              timestamp = 0L  // Don't expose timestamps
            ))
          }
        }
      }
      
      // Return messages in chronological order (oldest first for history)
      messages.reversed().take(limit)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to load conversation history from database", e)
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