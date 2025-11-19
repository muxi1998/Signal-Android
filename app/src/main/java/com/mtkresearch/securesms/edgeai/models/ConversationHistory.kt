package com.mtkresearch.securesms.edgeai.models

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * Models for conversation history in EdgeAI usecases
 */
data class ConversationHistory(
  val messages: List<HistoryMessage>,
  val threadId: Long
)

/**
 * A single message in conversation history.
 * Simplified version of DialogMessage for EdgeAI usecases.
 */
data class HistoryMessage(
  @JsonProperty("sender") val sender: String,
  @JsonProperty("text") val text: String,
  @JsonIgnore val timestamp: Long = 0L
)