package org.thoughtcrime.securesms.breeze.model

/**
 * Data model for a structured response from Breeze AI.
 * 
 * This model is parsed from the JSON string returned in the content
 * of an EdgeAI ChatResponse.
 */
data class BreezeResponse(
    val type: ResponseType,
    val text: String = "",
    val draftMessage: String? = null,
    val recipient: String? = null,
    val confirmationPrompt: String? = null
)
