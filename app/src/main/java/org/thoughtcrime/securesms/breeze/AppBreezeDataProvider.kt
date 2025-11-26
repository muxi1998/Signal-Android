package org.thoughtcrime.securesms.breeze

import android.content.Context
import com.mtkresearch.breeze.api.BreezeDataProvider
import com.mtkresearch.breeze.api.ConversationSummary
import com.mtkresearch.breeze.api.MessageSummary
import org.signal.core.util.logging.Log
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.thoughtcrime.securesms.database.SignalDatabase

/**
 * App-side implementation of BreezeDataProvider.
 * Provides read-only access to Signal's conversation data for Breeze AI.
 */
class AppBreezeDataProvider(private val context: Context) : BreezeDataProvider {
  
  companion object {
    private val TAG = Log.tag(AppBreezeDataProvider::class.java)
  }
  
  fun getConversationSummary(conversationId: Long): ConversationSummary? {
    return try {
      val recipient = SignalDatabase.threads.getRecipientForThreadId(conversationId)
      val threadRecord = SignalDatabase.threads.getThreadRecord(conversationId)

      ConversationSummary(
        id = conversationId,
        title = recipient?.getDisplayName(context),
        lastPreview = threadRecord?.body?.toString()
      )
    } catch (e: Exception) {
      Log.w(TAG, "Failed to get conversation summary", e)
      null
    }
  }

  fun getMessageSummary(messageId: Long): MessageSummary? {
    return try {
      val messageRecord = SignalDatabase.messages.getMessageRecord(messageId)

      MessageSummary(
        id = messageId,
        body = messageRecord.body,
        sender = messageRecord.fromRecipient.getDisplayName(context),
        timestamp = messageRecord.timestamp
      )
    } catch (e: Exception) {
      Log.w(TAG, "Failed to get message summary", e)
      null
    }
  }

  fun getRecentMessages(conversationId: Long, limit: Int): List<MessageSummary> {
    return try {
      val messages = mutableListOf<MessageSummary>()
      
      SignalDatabase.messages.getConversation(conversationId, 0, limit.toLong()).use { cursor ->
        while (cursor.moveToNext()) {
          val senderId = cursor.requireLong("from_recipient_id").toString()
          val body = cursor.requireString("body") ?: ""
          val timestamp = cursor.requireLong("date_received")
          
          if (body.isNotEmpty()) {
            messages.add(MessageSummary(
              id = cursor.requireLong("_id"),
              body = body,
              sender = senderId,
              timestamp = timestamp
            ))
          }
        }
      }
      
      // Return in chronological order (oldest first)
      messages.reversed().take(limit)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to load recent messages", e)
      emptyList()
    }
  }
}
