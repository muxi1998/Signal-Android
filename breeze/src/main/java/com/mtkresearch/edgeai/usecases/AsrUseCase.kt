package com.mtkresearch.breeze.edgeai.usecases

import com.mtkresearch.breeze.edgeai.EdgeAI
import kotlinx.coroutines.flow.Flow
import org.signal.core.util.logging.Log

/**
 * Use case for ASR (Automatic Speech Recognition).
 * 
 * Responsibilities:
 * - Handle speech-to-text conversion
 * - Support both file-based and microphone input
 * - Provide clean API for audio transcription
 * 
 * Example usage:
 * ```
 * val useCase = AsrUseCase()
 * val audioData = loadAudioFile() // or record from microphone
 * useCase.execute(audioData).collect { text ->
 *   println("Transcription: $text")
 * }
 * ```
 */
class AsrUseCase {
  
  companion object {
    private val TAG = Log.tag(AsrUseCase::class.java)
  }
  
  /**
   * Execute ASR request for audio file.
   * 
   * @param audioData Raw audio bytes
   * @param format Audio format (pcm, wav, etc.)
   * @param sampleRate Sample rate in Hz (default 16000)
   * @param language Language code (default "en")
   * @return Flow of transcribed text
   */
  fun execute(
    audioData: ByteArray,
    format: String = "pcm",
    sampleRate: Int = 16000,
    language: String = "en"
  ): Flow<String> {
    Log.d(TAG, "Executing ASR: format=$format, size=${audioData.size} bytes")
    
    return EdgeAI.asr(
      audioData = audioData,
      format = format,
      sampleRate = sampleRate,
      language = language
    )
  }
  
  /**
   * Execute ASR request for microphone input.
   * This is a convenience method that uses the same underlying implementation.
   * 
   * @param audioData Raw audio bytes from microphone
   * @param sampleRate Sample rate in Hz (default 16000)
   * @param language Language code (default "en")
   * @return Flow of transcribed text
   */
  fun executeFromMicrophone(
    audioData: ByteArray,
    sampleRate: Int = 16000,
    language: String = "en"
  ): Flow<String> {
    Log.d(TAG, "Executing ASR from microphone: size=${audioData.size} bytes")
    
    return execute(
      audioData = audioData,
      format = "pcm", // Microphone typically provides PCM
      sampleRate = sampleRate,
      language = language
    )
  }
}
