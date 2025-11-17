package org.thoughtcrime.securesms.mrutil.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * Represents a single message in conversation history.
 * Used for MR_util message transformation feasibility study.
 */
data class DialogMessage(
    @JsonProperty("sender") val sender: String,
    @JsonProperty("text") val text: String,
    @JsonIgnore val timestamp: Long = 0L
)