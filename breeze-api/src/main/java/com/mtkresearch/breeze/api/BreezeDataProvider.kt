package com.mtkresearch.breeze.api

/**
 * Data models for conversation and message summaries.
 */
data class ConversationSummary(
  val id: Long,
  val title: String?,
  val lastPreview: String?
)

data class MessageSummary(
  val id: Long,
  val body: String?,
  val sender: String?,
  val timestamp: Long
)

/**
 * Interface for providing read-only access to Signal's conversation data.
 * Implemented by the app module to bridge Signal's data layer with Breeze AI.
 */
interface BreezeDataProvider {
  /**
   * Get conversation summary by ID.
   * @param conversationId The thread ID
   * @return ConversationSummary or null if not found
   */
  fun getConversationSummary(conversationId: Long): ConversationSummary?

  /**
   * Get message summary by ID.
   * @param messageId The message ID
   * @return MessageSummary or null if not found
   */
  fun getMessageSummary(messageId: Long): MessageSummary?

  /**
   * Get recent messages from a conversation.
   * @param conversationId The thread ID
   * @param limit Maximum number of messages to retrieve
   * @return List of message summaries
   */
  fun getRecentMessages(conversationId: Long, limit: Int = 10): List<MessageSummary>
}
