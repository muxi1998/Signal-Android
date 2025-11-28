package com.mtkresearch.breeze.edgeai.models

/**
 * Simple wrapper for ASR request
 */
data class AsrRequest(
  val audioData: ByteArray,
  val format: String = "pcm", // pcm, wav, etc.
  val sampleRate: Int = 16000,
  val language: String = "en"
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as AsrRequest

    if (!audioData.contentEquals(other.audioData)) return false
    if (format != other.format) return false
    if (sampleRate != other.sampleRate) return false
    if (language != other.language) return false

    return true
  }

  override fun hashCode(): Int {
    var result = audioData.contentHashCode()
    result = 31 * result + format.hashCode()
    result = 31 * result + sampleRate
    result = 31 * result + language.hashCode()
    return result
  }
}

/**
 * Simple wrapper for ASR response
 */
data class AsrResponse(
  val text: String,
  val confidence: Float = 1.0f
)
