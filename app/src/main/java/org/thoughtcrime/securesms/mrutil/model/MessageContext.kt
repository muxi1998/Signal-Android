package org.thoughtcrime.securesms.mrutil.model

import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Provides context information needed for message transformation.
 * Used for MR_util message transformation feasibility study.
 */
data class MessageContext(
    val threadId: Long,
    val senderId: String,
    val recipientId: RecipientId,
    val isGroup: Boolean = false
)