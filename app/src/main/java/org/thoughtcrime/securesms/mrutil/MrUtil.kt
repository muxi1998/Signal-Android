package org.thoughtcrime.securesms.mrutil

import org.signal.core.util.logging.Log
import org.signal.core.util.requireLong
import org.signal.core.util.requireString
import org.thoughtcrime.securesms.mrutil.model.DialogMessage
import org.thoughtcrime.securesms.mrutil.model.MessageContext
import org.thoughtcrime.securesms.mrutil.model.TransformedMessage
import org.thoughtcrime.securesms.database.SignalDatabase

/**
 * Utility for MR_util message transformation.
 * Used for MR_util message transformation feasibility study.
 */
object MrUtil {
    private val TAG = Log.tag(MrUtil::class.java)
    
    /**
     * Main entry point for modifying outgoing messages
     * 
     * @param text Original message text
     * @param context Message context with thread info
     * @return Transformed message (JSON format or original text on failure)
     */
    fun modify(text: String, context: MessageContext): String {
        return try {
            if (text.isEmpty()) return text
            
            Log.d(TAG, "[MR_util] Modifying outgoing message for thread ${context.threadId} from sender ${context.senderId}: '$text'")
            
            // Get recent conversation history from Signal's database (last 10 messages)
            val recentMessages = getRecentConversationHistory(context.threadId, 10)
            
            // Create transformed message with recent history
            val transformedMessage = TransformedMessage(
                dialogHistory = recentMessages,
                text = text
            )
            
            val json = transformedMessage.toJson()
            Log.d(TAG, "[MR_util] Successfully transformed message to JSON with ${recentMessages.size} history items: ${recentMessages.map { "${it.sender}: ${it.text}" }}")
            
            json
        } catch (e: Exception) {
            Log.w(TAG, "Failed to modify message, returning original text", e)
            // Graceful fallback
            text
        }
    }
    
    /**
     * Get recent conversation history from Signal's database
     */
    private fun getRecentConversationHistory(threadId: Long, limit: Int): List<DialogMessage> {
        return try {
            val messages = mutableListOf<DialogMessage>()
            SignalDatabase.messages.getConversation(threadId, 0, limit.toLong()).use { cursor ->
                while (cursor.moveToNext()) {
                    val senderId = cursor.requireLong("from_recipient_id").toString()
                    val body = cursor.requireString("body") ?: ""
                    
                    if (body.isNotEmpty()) {
                        // Extract raw text if this is a JSON message, otherwise use body as-is
                        val rawText = extractRawText(body)
                        messages.add(DialogMessage(
                            sender = senderId,
                            text = rawText,
                            timestamp = 0L  // Don't show timestamps in display
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
     * Extract raw text from a message, handling both JSON and plain text
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