package com.mtkresearch.breeze.edgeai.models

/**
 * Simple wrapper for TTS request
 */
data class TtsRequest(
  val text: String,
  val voice: String = "alloy",
  val speed: Float = 1.0f,
  val format: String = "pcm"
)

/**
 * Simple wrapper for TTS response
 */
data class TtsResponse(
  val audioData: ByteArray = byteArrayOf(),
  val format: String = "engine_playback", // Engine handles playback
  val isComplete: Boolean = true
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TtsResponse

    if (!audioData.contentEquals(other.audioData)) return false
    if (format != other.format) return false
    if (isComplete != other.isComplete) return false

    return true
  }

  override fun hashCode(): Int {
    var result = audioData.contentHashCode()
    result = 31 * result + format.hashCode()
    result = 31 * result + isComplete.hashCode()
    return result
  }
}
