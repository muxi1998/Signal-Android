package com.mtkresearch.breeze.edgeai.usecases

import com.mtkresearch.breeze.edgeai.EdgeAI
import com.mtkresearch.breeze.edgeai.models.TtsResponse
import kotlinx.coroutines.flow.Flow
import org.signal.core.util.logging.Log

/**
 * Use case for TTS (Text-to-Speech).
 * 
 * Responsibilities:
 * - Handle text-to-speech conversion
 * - Support different voices and speeds
 * - Provide clean API for audio synthesis
 * 
 * Note: BreezeApp Engine handles audio playback directly.
 * The response indicates completion rather than returning audio data.
 * 
 * Example usage:
 * ```
 * val useCase = TtsUseCase()
 * useCase.execute("Hello, world!").collect { response ->
 *   println("TTS complete: ${response.isComplete}")
 * }
 * ```
 */
class TtsUseCase {
  
  companion object {
    private val TAG = Log.tag(TtsUseCase::class.java)
  }
  
  /**
   * Execute TTS request.
   * 
   * @param text Text to convert to speech
   * @param voice Voice identifier (default "alloy")
   * @param speed Speech speed (0.5 to 2.0, default 1.0)
   * @return Flow of TTS responses
   */
  fun execute(
    text: String,
    voice: String = "alloy",
    speed: Float = 1.0f
  ): Flow<TtsResponse> {
    Log.d(TAG, "Executing TTS: text='$text', voice=$voice, speed=$speed")
    
    return EdgeAI.tts(
      text = text,
      voice = voice,
      speed = speed
    )
  }
}
