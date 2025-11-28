package com.mtkresearch.breeze.edgeai.usecases

import com.mtkresearch.breeze.edgeai.EdgeAI
import kotlinx.coroutines.flow.Flow
import org.signal.core.util.logging.Log

/**
 * Use case for streaming chat with LLM.
 * 
 * Responsibilities:
 * - Handle streaming chat requests
 * - Provide clean API for conversational AI
 * - Support conversation history
 * 
 * Example usage:
 * ```
 * val useCase = StreamingChatUseCase()
 * useCase.execute("Hello, how are you?").collect { token ->
 *   print(token) // Prints tokens as they arrive
 * }
 * ```
 */
class StreamingChatUseCase {
  
  companion object {
    private val TAG = Log.tag(StreamingChatUseCase::class.java)
  }
  
  /**
   * Execute a streaming chat request.
   * 
   * @param prompt User's input prompt
   * @param systemPrompt Optional system prompt to guide the AI
   * @param temperature Controls randomness (0.0 to 2.0, default 0.7)
   * @param maxTokens Maximum tokens to generate
   * @param model Model identifier (empty = engine decides)
   * @return Flow of text tokens as they are generated
   */
  fun execute(
    prompt: String,
    systemPrompt: String = "You are a helpful AI assistant.",
    temperature: Float = 0.7f,
    maxTokens: Int? = null,
    model: String = ""
  ): Flow<String> {
    Log.d(TAG, "Executing streaming chat: prompt='$prompt'")
    
    return EdgeAI.chat(
      prompt = prompt,
      systemPrompt = systemPrompt,
      temperature = temperature,
      maxTokens = maxTokens,
      model = model
    )
  }
}
