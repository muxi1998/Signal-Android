package org.thoughtcrime.securesms.breeze

import android.content.Context
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.MessageType
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.sms.MessageSender

object FakeAIResponder {

    private val TAG = Log.tag(FakeAIResponder::class.java)

    @JvmStatic
    fun onAiCommand(context: Context, userMessage: OutgoingMessage, threadId: Long) {
        val body = userMessage.body ?: return

        Log.i(TAG, "Received AI command (Fake): $body")

        if (body.startsWith("@ai yes")) {
            handleConfirmation(context)
            return
        } else if (body.startsWith("@ai no")) {
            handleRejection(context)
            return
        }

        // Always generate fake response
        val responseText = generateFakeResponse(body, userMessage, threadId)
        sendAiResponse(context, responseText, threadId)
    }

    internal fun handleConfirmation(context: Context) {
        val draftManager = DraftManager.getInstance()
        if (draftManager.hasPendingDraft()) {
            val draft = draftManager.pendingDraft
            val threadId = draftManager.pendingThreadId
            
            if (draft != null) {
                Log.i(TAG, "Confirming draft: ${draft.body}")
                // Send the draft
                MessageSender.send(context, draft, threadId, MessageSender.SendType.SIGNAL, null, null)
                draftManager.clearDraft()
                // No extra AI confirmation message needed, just the sent message is enough
            }
        } else {
            Log.w(TAG, "No draft to confirm.")
        }
    }

    internal fun handleRejection(context: Context) {
        val draftManager = DraftManager.getInstance()
        if (draftManager.hasPendingDraft()) {
            val threadId = draftManager.pendingThreadId
            draftManager.clearDraft()
            // Just discard silently or maybe a small toast in real app, but for now silent is cleaner as requested
        }
    }

    private fun generateFakeResponse(command: String, originalMessage: OutgoingMessage, threadId: Long): String {
        return when {
            command.startsWith("@ai translate:") -> {
                val textToTranslate = command.substring("@ai translate:".length).trim()
                "[AI Mock] Translation: $textToTranslate"
            }
            command.startsWith("@ai summarize") -> {
                "[AI Mock] Summary: This conversation is about testing."
            }
            command.startsWith("@ai help") -> {
                "[AI Mock] Commands: @ai chat, translate, summarize, tell, help"
            }
            command.startsWith("@ai tell") -> {
                val content = command.substring("@ai tell".length).trim()
                
                // Create draft (clone original message but change body)
                // DraftManager expects OutgoingMessage. 
                // Since OutgoingMessage is Kotlin and we are in Kotlin, we can copy properties easily or use constructor.
                // Re-using the constructor with minimal info for the draft.
                // We need to keep the recipient same as original thread message.
                
                // Create draft using the smaller constructor for compatibility
                val pendingDraft = OutgoingMessage(
                   recipient = originalMessage.threadRecipient,
                   body = content,
                   timestamp = System.currentTimeMillis(),
                   expiresIn = originalMessage.expiresIn,
                   viewOnce = originalMessage.isViewOnce,
                   distributionType = originalMessage.distributionType,
                   quote = originalMessage.outgoingQuote,
                   mentions = emptyList<org.thoughtcrime.securesms.database.model.Mention>(),
                   attachments = emptyList<org.thoughtcrime.securesms.attachments.Attachment>(),
                   networkFailures = emptySet<org.thoughtcrime.securesms.database.documents.NetworkFailure>(),
                   mismatches = emptySet<org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch>()
                )
                
                DraftManager.getInstance().setPendingDraft(pendingDraft, threadId)

                "[AI Mock] Drafted: \"$content\". Send? @ai yes/no"
            }
            else -> "[AI Mock] Unknown command."
        }
    }
    
    // Fix generateResponse to handle draft saving properly (needs threadId)
    // Refactoring generateResponse to return response text, and separate draft logic?
    // Or just inline draft logic in main method.
    // Let's keep it simple. I will just pass threadId to generateResponse just for the draft logic.
    
    private fun sendAiResponse(context: Context, responseText: String, threadId: Long) {
        try {
            val threadRecipient = SignalDatabase.threads.getRecipientForThreadId(threadId) ?: return

            val incomingMessage = IncomingMessage(
                type = MessageType.NORMAL,
                from = threadRecipient.id,
                sentTimeMillis = System.currentTimeMillis(),
                serverTimeMillis = System.currentTimeMillis(),
                receivedTimeMillis = System.currentTimeMillis(),
                body = responseText,
                isUnidentified = false
            )
            
            SignalDatabase.messages.insertMessageInbox(incomingMessage, threadId)
            
            // Notify thread update
            SignalDatabase.threads.update(threadId, true, true)
             
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert AI response", e)
        }
    }
}
