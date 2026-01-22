package org.thoughtcrime.securesms.breeze

import android.content.Context
import com.mtkresearch.breeze.api.BreezeDataProvider
import com.mtkresearch.breeze.api.ConversationSummary
import com.mtkresearch.breeze.api.MessageSummary
import org.signal.core.util.logging.Log
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.database.model.MmsMessageRecord

/**
 * App-side implementation of BreezeDataProvider.
 * Provides read-only access to Signal's conversation data for Breeze AI.
 */
class AppBreezeDataProvider(private val context: Context) : BreezeDataProvider {
  
  companion object {
    private val TAG = Log.tag(AppBreezeDataProvider::class.java)
  }
  
  override fun getConversationSummary(conversationId: Long): ConversationSummary? {
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

  override fun getMessageSummary(messageId: Long): MessageSummary? {
    return try {
      val messageRecord = SignalDatabase.messages.getMessageRecord(messageId)
      val sender = messageRecord.fromRecipient

      MessageSummary(
        messageId = messageId,
        threadId = messageRecord.threadId,
        messageBody = messageRecord.body,
        senderDisplayName = sender.getDisplayName(context),
        isFromLocalUser = sender.isSelf,
        quotedMessageBody = (messageRecord as? MmsMessageRecord)?.quote?.displayText?.toString(),
        timestamp = messageRecord.timestamp
      )
    } catch (e: Exception) {
      Log.w(TAG, "Failed to get message summary", e)
      null
    }
  }

  override fun getRecentMessages(conversationId: Long, limit: Int): List<MessageSummary> {
    return try {
      val messages = mutableListOf<MessageSummary>()
      
      SignalDatabase.messages.getConversation(conversationId, 0, limit.toLong()).use { cursor ->
        while (cursor.moveToNext()) {
            
          val id = cursor.requireLong("_id")
          val threadId = cursor.requireLong("thread_id")
          val senderId = cursor.requireLong("from_recipient_id")
          val body = cursor.requireString("body") ?: ""
          val timestamp = cursor.requireLong("date_received")
          val quoteBody = cursor.requireString("quote_body")
          
          val senderRecipient = Recipient.resolved(RecipientId.from(senderId))
          
          if (body.isNotEmpty()) {
            messages.add(MessageSummary(
              messageId = id,
              threadId = threadId,
              messageBody = body,
              senderDisplayName = senderRecipient.getDisplayName(context),
              isFromLocalUser = senderRecipient.isSelf,
              quotedMessageBody = quoteBody,
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
