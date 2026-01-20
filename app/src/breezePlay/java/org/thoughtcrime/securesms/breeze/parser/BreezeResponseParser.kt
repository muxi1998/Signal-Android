package org.thoughtcrime.securesms.breeze.parser

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.thoughtcrime.securesms.breeze.model.BreezeResponse
import org.thoughtcrime.securesms.breeze.model.ResponseType
import org.signal.core.util.logging.Log

/**
 * Utility for parsing JSON responses from Breeze AI using Jackson.
 * Compatible with JVM unit tests.
 */
object BreezeResponseParser {

    private val TAG = Log.tag(BreezeResponseParser::class.java)
    private val objectMapper = ObjectMapper().registerKotlinModule()

    /**
     * Parses a JSON string into a [BreezeResponse] object.
     * 
     * @param jsonString The raw JSON string from the AI's response content.
     * @return A structured [BreezeResponse] or null if parsing fails.
     */
    fun parse(jsonString: String?): BreezeResponse? {
        if (jsonString.isNullOrBlank()) return null

        return try {
            val root: JsonNode = objectMapper.readTree(jsonString)
            val typeStr = root.get("type")?.asText() ?: "response"
            val type = if (typeStr.lowercase() == "draft") ResponseType.DRAFT else ResponseType.RESPONSE

            if (type == ResponseType.DRAFT) {
                BreezeResponse(
                    type = type,
                    draftMessage = root.get("draft_message")?.asText() ?: "",
                    recipient = root.get("recipient")?.asText() ?: "",
                    confirmationPrompt = root.get("confirmation_prompt")?.asText() ?: "Should I send this message?"
                )
            } else {
                BreezeResponse(
                    type = type,
                    text = root.get("text")?.asText() ?: ""
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Breeze AI response: $jsonString", e)
            null
        }
    }
}
