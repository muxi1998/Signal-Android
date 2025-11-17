package org.thoughtcrime.securesms.mrutil.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * JSON structure for messages with dialog history.
 * Used for MR_util message transformation feasibility study.
 */
data class TransformedMessage(
    @JsonProperty("dialog_history") val dialogHistory: List<DialogMessage>,
    @JsonProperty("text") val text: String
) {

    /**
     * Convert this object to JSON string
     */
    fun toJson(): String {
        return jacksonObjectMapper().writeValueAsString(this)
    }

    companion object {
        /**
         * Parse JSON string to TransformedMessage
         */
        fun fromJson(json: String): TransformedMessage {
            return jacksonObjectMapper().readValue(json, TransformedMessage::class.java)
        }
    }
}