package com.mtkresearch.breeze

import org.json.JSONObject
import org.signal.core.util.logging.Log

/**
 * Parses JSON responses from BreezeApp-engine (EdgeAI).
 */
object BreezeResponseParser {
    
    private val TAG = Log.tag(BreezeResponseParser::class.java)

    data class ParsedResponse(
        val type: String,
        val text: String?,
        val draftMessage: String? = null,
        val recipient: String? = null,
        val confirmationPrompt: String? = null
    )

    fun parse(jsonString: String): ParsedResponse? {
        return try {
            // First cleanup: find the first '{' and last '}' to handle potential preamble/postamble text
            val firstBrace = jsonString.indexOf('{')
            val lastBrace = jsonString.lastIndexOf('}')
            
            val validJsonString = if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                jsonString.substring(firstBrace, lastBrace + 1)
            } else {
                jsonString
            }

            val json = JSONObject(validJsonString)
            val type = json.optString("type")
            val text = json.optString("text")
            
            // Extract draft fields
            val draftMessage = json.optString("draft_message").takeIf { it.isNotEmpty() }
            val recipient = json.optString("recipient").takeIf { it.isNotEmpty() }
            val confirmationPrompt = json.optString("confirmation_prompt").takeIf { it.isNotEmpty() }
            
            ParsedResponse(
                type = type,
                text = text,
                draftMessage = draftMessage,
                recipient = recipient,
                confirmationPrompt = confirmationPrompt
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse AI response as JSON: ${e.message}")
            null
        }
    }
}
