package com.mtkresearch.breeze

import com.mtkresearch.breeze.api.BreezeDataProvider
import org.signal.core.util.logging.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds chat context with system prompt and conversation history from Signal DB.
 */
object SignalContextBuilder {
    
    private val TAG = Log.tag(SignalContextBuilder::class.java)
    
    // Robust Prompt Formatting (Llama-2 Style)
    /**
     * Build the System Prompt with embedded conversation history.
     */
    fun buildSystemPromptWithHistory(
        dataProvider: BreezeDataProvider?,
        threadId: Long
    ): String {
        
        // 1. Build History JSON
        val historyJson = if (dataProvider != null && threadId > 0) {
            buildHistoryJson(dataProvider, threadId)
        } else {
            "[]"
        }

        // 2. Construct System Prompt (Static Instructions + History Context)
        return StringBuilder()
            .append(BreezeConfig.SYSTEM_PROMPT)
            .append(JSON_Format_Instruction)
            .append(historyJson)
            .append(End_Context)
            .toString()
    }

    private const val JSON_Format_Instruction = "\n\n[Conversation Context]\nThe following is the conversation history using JSON format (this is background information):\n"
    private const val End_Context = "\n[End Context]\nRespond to the final user input based on this context."


    private fun buildHistoryJson(dataProvider: BreezeDataProvider, threadId: Long): String {
        return try {
            val recentMessages = dataProvider.getRecentMessages(
                threadId, 
                BreezeConfig.EDGEAI_HISTORY_LIMIT
            )
            
            val jsonArray = JSONArray()
            
            for (msg in recentMessages) {
                val messageJson = JSONObject()
                val body = msg.messageBody ?: ""
                
                // Multi-turn Strategy: Detect AI responses via prefix
                val senderName = if (body.startsWith("[AI]")) {
                    "Breeze AI"
                } else if (msg.isFromLocalUser) {
                    "Me" 
                } else {
                    msg.senderDisplayName ?: "Unknown"
                }

                messageJson.put("thread_id", msg.threadId)
                messageJson.put("sender", senderName)
                messageJson.put("body", body)
                messageJson.put("quote", msg.quotedMessageBody ?: JSONObject.NULL)
                
                jsonArray.put(messageJson)
            }
            
            jsonArray.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to build history JSON", e)
            "[]"
        }
    }
}
