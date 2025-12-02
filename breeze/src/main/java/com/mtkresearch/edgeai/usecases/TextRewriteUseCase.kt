package com.mtkresearch.breeze.edgeai.usecases

import com.mtkresearch.breeze.BreezeConfig
import com.mtkresearch.breeze.ToneType
import com.mtkresearch.breeze.edgeai.EdgeAI
import com.mtkresearch.breeze.edgeai.utils.HistoryExtractor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.signal.core.util.logging.Log

/**
 * Use case for text rewriting with different tones using LLM.
 * 
 * Handles all tone transformations (FORMAL, FRIENDLY, CLARITY, SHORTEN, EXPAND, HISTORY_JSON)
 * using a single implementation with tone-specific prompts.
 * 
 * Example usage:
 * ```
 * val useCase = TextRewriteUseCase()
 * useCase.execute(
 *   text = "hey can we meet",
 *   toneType = ToneType.FORMAL,
 *   threadId = 123
 * ).collect { rewrittenText ->
 *   println(rewrittenText) // "Would it be possible to schedule a meeting?"
 * }
 * ```
 */
class TextRewriteUseCase {
  
  companion object {
    private val TAG = Log.tag(TextRewriteUseCase::class.java)
  }
  
  /**
   * Execute text rewrite with specified tone.
   * 
   * @param text Original text to rewrite
   * @param toneType Type of tone transformation
   * @param threadId Optional conversation thread ID for context-aware rewrites
   * @return Flow of rewritten text (streaming tokens)
   */
  fun execute(
    text: String,
    toneType: ToneType,
    threadId: Long? = null
  ): Flow<String> {
    Log.d(TAG, "Executing text rewrite: tone=$toneType, hasContext=${threadId != null}")
    
    // Build prompt based on tone type
    val prompt = buildPrompt(text, toneType, threadId)
    val systemPrompt = buildSystemPrompt(toneType)
    
    Log.d(TAG, "System prompt: $systemPrompt")
    Log.d(TAG, "User prompt: $prompt")
    
    // Use EdgeAI streaming chat
    return EdgeAI.chat(
      prompt = prompt,
      systemPrompt = systemPrompt,
      temperature = BreezeConfig.EDGEAI_REWRITE_TEMPERATURE,
      maxTokens = BreezeConfig.EDGEAI_REWRITE_MAX_TOKENS
    ).map { token ->
      // Accumulate tokens for streaming display
      token
    }
  }
  
  /**
   * Build system prompt based on tone type.
   * Sets the AI's role and behavior.
   */
  private fun buildSystemPrompt(toneType: ToneType): String {
    return when (toneType) {
      ToneType.FORMAL -> 
        "You are a professional writing assistant. Rewrite messages in a formal, professional tone."
      
      ToneType.FRIENDLY -> 
        "You are a friendly writing assistant. Rewrite messages in a warm, conversational tone."
      
      ToneType.CLARITY -> 
        "You are a clarity expert. Improve message clarity by making them direct and unambiguous."
      
      ToneType.SHORTEN -> 
        "You are a conciseness expert. Make messages brief while preserving essential meaning."
      
      ToneType.EXPAND -> 
        "You are a detail-oriented writing assistant. Expand messages with helpful context and detail."
      
      ToneType.HISTORY_JSON ->
        "You are a context-aware writing assistant. Rewrite messages considering conversation history."
    }
  }
  
  /**
   * Build user prompt with tone-specific instructions.
   * Includes conversation history for HISTORY_JSON tone.
   */
  private fun buildPrompt(text: String, toneType: ToneType, threadId: Long?): String {
    // For HISTORY_JSON, include conversation context
    if (toneType == ToneType.HISTORY_JSON && threadId != null) {
      return buildHistoryAwarePrompt(text, threadId)
    }
    
    // Standard tone prompts
    return when (toneType) {
      ToneType.FORMAL -> """
        Rewrite the following message in a formal, professional tone.
        Keep the core meaning but use formal language and structure.
        Only output the rewritten message, nothing else.
        
        Original message: $text
        
        Rewritten message:
      """.trimIndent()
      
      ToneType.FRIENDLY -> """
        Rewrite the following message in a warm, friendly, conversational tone.
        Make it sound approachable and casual while keeping the meaning.
        Only output the rewritten message, nothing else.
        
        Original message: $text
        
        Rewritten message:
      """.trimIndent()
      
      ToneType.CLARITY -> """
        Improve the clarity of the following message.
        Make it clearer, more direct, and easier to understand. Remove ambiguity.
        Only output the improved message, nothing else.
        
        Original message: $text
        
        Improved message:
      """.trimIndent()
      
      ToneType.SHORTEN -> """
        Make the following message more concise.
        Keep the essential meaning but remove unnecessary words. Be brief.
        Only output the shortened message, nothing else.
        
        Original message: $text
        
        Shortened message:
      """.trimIndent()
      
      ToneType.EXPAND -> """
        Expand the following message with more detail and context.
        Add helpful information while maintaining the core message.
        Only output the expanded message, nothing else.
        
        Original message: $text
        
        Expanded message:
      """.trimIndent()
      
      ToneType.HISTORY_JSON -> {
        // Fallback if no threadId provided
        "Rewrite this message: $text"
      }
    }
  }
  
  /**
   * Build context-aware prompt using conversation history.
   */
  private fun buildHistoryAwarePrompt(text: String, threadId: Long): String {
    return try {
      // Extract conversation history
      val history = HistoryExtractor.extractHistory(
        threadId = threadId,
        limit = BreezeConfig.EDGEAI_REWRITE_HISTORY_LIMIT
      )
      
      if (history.messages.isEmpty()) {
        // No history available, use simple prompt
        return "Rewrite this message: $text"
      }
      
      // Build history context
      val historyContext = history.messages.joinToString("\n") { msg ->
        "${msg.sender}: ${msg.text}"
      }
      
      """
        Given this recent conversation history:
        
        $historyContext
        
        Rewrite the user's message to be contextually appropriate and natural:
        
        Original message: $text
        
        Rewritten message:
      """.trimIndent()
      
    } catch (e: Exception) {
      Log.e(TAG, "Failed to extract history for context-aware rewrite", e)
      // Fallback to simple prompt
      "Rewrite this message: $text"
    }
  }
}
