package org.thoughtcrime.securesms.breeze

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.MessageType
import org.thoughtcrime.securesms.mms.IncomingMessage
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.sms.MessageSender
import com.mtkresearch.breeze.EdgeAIClient
import com.mtkresearch.breeze.SignalContextBuilder
import com.mtkresearch.breeze.BreezeResponseParser

/**
 * Handles AI commands using the real EdgeAI engine.
 */
object BreezeAIResponder {

    private val TAG = Log.tag(BreezeAIResponder::class.java)
    private val scope = CoroutineScope(Dispatchers.IO)

    @JvmStatic
    fun onAiCommand(context: Context, userMessage: OutgoingMessage, threadId: Long) {
        val body = userMessage.body ?: return
        Log.i(TAG, "Received AI command (Real): $body")

        if (body.startsWith("@ai", ignoreCase = true)) {
            val command = parseCommand(body)
            when (command) {
                AiCommand.CONFIRM -> {
                    handleConfirmation(context, threadId)
                    return
                }
                AiCommand.REJECT -> {
                    handleRejection(context, threadId)
                    return
                }
                null -> {
                    // Not a control command, treat as chat prompt
                    handleEdgeAIChat(context, body, threadId)
                }
            }
        }
    }

    private enum class AiCommand {
        CONFIRM, REJECT
    }

    private fun parseCommand(input: String): AiCommand? {
        // Remove @ai prefix and whitespace
        val content = input.substring(3).trim().lowercase()
        
        // precise match or prefix match could be dangerous for chat, so let's stick to set matching
        // or check if it *is* one of the keywords.
        
        val confirmKeywords = setOf(
            "yes", "y", "ok", "okay", "sure", "confirm", "send", 
            "是", "好", "好的", "發送", "发送", "確認", "确认", "没问题", "沒有問題"
        )
        
        val rejectKeywords = setOf(
            "no", "n", "cancel", "reject", "abort", "don't", "dont",
            "否", "不", "取消", "不要"
        )

        if (content in confirmKeywords) return AiCommand.CONFIRM
        if (content in rejectKeywords) return AiCommand.REJECT
        
        return null
    }

    private fun handleConfirmation(context: Context, threadId: Long) {
        val draftManager = DraftManager.getInstance()
        if (draftManager.hasPendingDraft()) {
            val draft = draftManager.pendingDraft
            // Note: draftManager.pendingThreadId usually matches threadId, but strictly we should use the one where command was issued or where draft belongs.
            // Assuming draft belongs to the thread we are confirming in.
            
            if (draft != null) {
                Log.i(TAG, "Confirming draft: ${draft.body}")
                MessageSender.send(context, draft, threadId, MessageSender.SendType.SIGNAL, null, null)
                draftManager.clearDraft()
            }
        } else {
            Log.w(TAG, "No draft to confirm.")
            sendAiResponse(context, "[AI] 沒有待傳送的訊息草稿。", threadId)
        }
    }

    private fun handleRejection(context: Context, threadId: Long) {
        val draftManager = DraftManager.getInstance()
        if (draftManager.hasPendingDraft()) {
            draftManager.clearDraft()
            sendAiResponse(context, "[AI] 已取消草稿。", threadId)
        } else {
             sendAiResponse(context, "[AI] 沒有待取消的訊息草稿。", threadId)
        }
    }

    private fun handleEdgeAIChat(context: Context, prompt: String, threadId: Long) {
        scope.launch {
            try {
                if (!EdgeAIClient.isReady()) {
                    val initialized = EdgeAIClient.initialize(context)
                    if (!initialized) {
                        sendAiResponse(context, "[AI] 無法連接到 BreezeApp-engine。請確認已安裝並運行。", threadId)
                        return@launch
                    }
                }

                val dataProvider = try {
                    AppBreezeDataProvider(context)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not create data provider", e)
                    null
                }
                
                val systemPrompt = SignalContextBuilder.buildSystemPromptWithHistory(
                    dataProvider = dataProvider,
                    threadId = threadId
                )

                val result = EdgeAIClient.chat(prompt, systemPrompt)
                
                result.fold(
                    onSuccess = { response ->
                        Log.i(TAG, "EdgeAI Raw Response: $response")
                        val parsed = BreezeResponseParser.parse(response)
                        
                        if (parsed != null) {
                            when (parsed.type) {
                                "response" -> {
                                    val text = parsed.text
                                    sendAiResponse(context, "[AI] ${text ?: ""}", threadId)
                                }
                                "draft" -> {
                                    val draftBody = parsed.draftMessage
                                    val confirmPrompt = parsed.confirmationPrompt ?: "Should I send this message?"
                                    
                                    if (!draftBody.isNullOrEmpty()) {
                                        try {
                                            val threadRecipient = SignalDatabase.threads.getRecipientForThreadId(threadId)
                                            if (threadRecipient != null) {
                                                val pendingDraft = OutgoingMessage(
                                                   recipient = threadRecipient,
                                                   body = draftBody,
                                                   timestamp = System.currentTimeMillis(),
                                                   expiresIn = 0,
                                                   viewOnce = false,
                                                   distributionType = org.thoughtcrime.securesms.database.ThreadTable.DistributionTypes.DEFAULT,
                                                   quote = null,
                                                   mentions = emptyList(),
                                                   attachments = emptyList(),
                                                   networkFailures = emptySet(),
                                                   mismatches = emptySet()
                                                )
                                                
                                                DraftManager.getInstance().setPendingDraft(pendingDraft, threadId)
                                                sendAiResponse(context, "[AI] $confirmPrompt\n(Reply '@ai yes' or '@ai no')\n\nDraft: \"$draftBody\"", threadId)
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Failed to create draft", e)
                                        }
                                    }
                                }
                                else -> sendAiResponse(context, "[AI] ${parsed.text ?: response}", threadId)
                            }
                        } else {
                            sendAiResponse(context, "[AI] $response", threadId)
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "EdgeAI chat failed", error)
                        sendAiResponse(context, "[AI] 錯誤: ${error.message ?: "未知錯誤"}", threadId)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in EdgeAI chat", e)
                sendAiResponse(context, "[AI] 發生錯誤，請稍後再試。", threadId)
            }
        }
    }

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
            SignalDatabase.threads.update(threadId, true, true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert AI response", e)
        }
    }
    suspend fun transcribeVoiceNote(context: Context, uri: android.net.Uri): Result<String> {
        return try {
            val audioBytes = try {
                 org.thoughtcrime.securesms.providers.BlobProvider.getInstance().getStream(context, uri).use { it.readBytes() }
            } catch (e: Exception) {
                 Log.w(TAG, "Failed to read audio blob", e)
                 return Result.failure(e)
            }

            // Ensure initialized before use
            if (!EdgeAIClient.isReady()) {
                val initialized = EdgeAIClient.initialize(context)
                if (!initialized) {
                    return Result.failure(IllegalStateException("Failed to initialize EdgeAI"))
                }
            }

            EdgeAIClient.asr(audioBytes)
        } catch (e: Exception) {
            Log.e(TAG, "ASR process failed", e)
            Result.failure(e)
        }
    }
}
