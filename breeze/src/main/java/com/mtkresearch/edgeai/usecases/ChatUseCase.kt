package com.mtkresearch.breeze.edgeai.usecases

import com.mtkresearch.breeze.BreezeConfig
import com.mtkresearch.breeze.edgeai.EdgeAI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.signal.core.util.logging.Log

/**
 * Use case for conversational chat with Charles (AI assistant).
 *
 * This is separate from TextRewriteUseCase which handles tone transformations.
 * ChatUseCase is for actual dialogue - asking questions, requesting help, etc.
 * Tone transformations (FORMAL, FRIENDLY, etc.) are tools used within this chat context.
 *
 * Example usage:
 * ```
 * val useCase = ChatUseCase()
 * useCase.execute(
 *   userMessage = "Help me write a letter to my boss",
 *   conversationHistory = "...",
 *   currentDraft = "..."
 * ).collect { response ->
 *   println(response)
 * }
 * ```
 */
class ChatUseCase {

  companion object {
    private val TAG = Log.tag(ChatUseCase::class.java)

    private const val SYSTEM_PROMPT = """You are Charles, a helpful AI assistant for messaging. Your role is to help users compose and refine their messages.

When the user provides:
- A request to write something: Draft a message for them
- Context about a conversation: Use it to craft an appropriate response
- A question: Answer helpfully and suggest how it relates to their messaging needs
- A current draft: Help improve or modify it based on their request

Be conversational, helpful, and focused on producing useful message drafts. Output the draft message directly without extra explanation unless asked."""
  }

  /**
   * Execute a chat conversation with Charles.
   *
   * @param userMessage The user's message/request
   * @param conversationHistory Optional history of the Charles conversation (not Signal thread)
   * @param currentDraft Optional current draft being worked on
   * @return Flow of response tokens (streaming)
   */
  fun execute(
    userMessage: String,
    conversationHistory: String? = null,
    currentDraft: String? = null
  ): Flow<String> {
    Log.d(TAG, "Executing chat: message='${userMessage.take(50)}...', hasHistory=${!conversationHistory.isNullOrBlank()}, hasDraft=${!currentDraft.isNullOrBlank()}")

    // Build the prompt with context
    val prompt = buildPrompt(userMessage, conversationHistory, currentDraft)

    Log.d(TAG, "Chat prompt: ${prompt.take(200)}...")

    // Use EdgeAI streaming chat
    return EdgeAI.chat(
      prompt = prompt,
      systemPrompt = SYSTEM_PROMPT,
      temperature = BreezeConfig.EDGEAI_REWRITE_TEMPERATURE,
      maxTokens = BreezeConfig.EDGEAI_REWRITE_MAX_TOKENS
    ).map { token ->
      token
    }
  }

  /**
   * Build the user prompt with available context.
   */
  private fun buildPrompt(
    userMessage: String,
    conversationHistory: String?,
    currentDraft: String?
  ): String {
    return buildString {
      // Include conversation history if available
      if (!conversationHistory.isNullOrBlank()) {
        appendLine("Previous conversation with you:")
        appendLine(conversationHistory)
        appendLine()
      }

      // Include current draft if available
      if (!currentDraft.isNullOrBlank()) {
        appendLine("Current draft:")
        appendLine(currentDraft)
        appendLine()
      }

      // The user's message/request
      appendLine("User's request:")
      appendLine(userMessage)
    }.trim()
  }
}
