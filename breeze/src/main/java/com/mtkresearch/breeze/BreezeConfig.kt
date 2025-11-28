package com.mtkresearch.breeze

/**
 * Configuration object for Breeze AI Floating Assistant feature.
 *
 * This provides centralized configuration for the AI floating assistant feature,
 * including feature flags, performance settings, and service configuration.
 */
object BreezeConfig {

  /**
   * Master feature flag for Breeze AI Assistant.
   * When false, all Breeze functionality is disabled.
   */
  const val FEATURE_ENABLED = true // Set to false to disable entirely

  /**
   * AI Service Configuration
   */
  const val AI_SERVICE_TIMEOUT_MS = 10000L // 10 seconds max for AI responses
  const val AI_SERVICE_RETRY_COUNT = 2
  const val MAX_TEXT_LENGTH = 5000 // Maximum characters for AI processing

  /**
   * Performance Thresholds
   */
  const val GESTURE_RESPONSE_TIMEOUT_MS = 100L // Must respond within 100ms
  const val SPARK_ICON_SHOW_DELAY_MS = 200L // Delay before showing spark icon
  const val SESSION_CLEANUP_DELAY_MS = 1000L // Time before cleaning up sessions

  /**
   * UI Configuration
   */
  const val FLOATING_WINDOW_BLUR_RADIUS = 5f // Reduced blur for better visibility
  const val SPARK_ICON_SIZE_DP = 36 // Increased from 20 to 36 for better visibility
  const val SWIPE_THRESHOLD_DP = 50f
  const val PARTICLE_COUNT = 8

  /**
   * Security Configuration
   */
  const val ENABLE_SENSITIVE_FIELD_DETECTION = true
  val SENSITIVE_FIELD_TYPES = setOf(
    "password",
    "creditCard",
    "ssn",
    "pin"
  )

  /**
   * Debug Configuration
   * Note: Library modules don't have access to app BuildConfig
   * Debug features are disabled by default in library
   */
  const val DEBUG_OVERLAY_ENABLED = false
  const val DEBUG_LOGGING_ENABLED = false

  /**
   * Session Management
   */
  const val MAX_SESSIONS_PER_APP = 3
  const val MAX_SUGGESTIONS_PER_SESSION = 10
  const val SESSION_MEMORY_LIMIT_MB = 2L
  
  /**
   * EdgeAI Configuration
   */
  const val EDGEAI_INIT_TIMEOUT_MS = 10000L // 10 seconds for initialization
  const val EDGEAI_DEFAULT_TEMPERATURE = 0.7f // Default LLM temperature
  const val EDGEAI_DEFAULT_MAX_TOKENS = 2048 // Default max tokens for chat
  const val EDGEAI_DEFAULT_VOICE = "alloy" // Default TTS voice
  const val EDGEAI_DEFAULT_SPEED = 1.0f // Default TTS speed
  const val EDGEAI_ASR_SAMPLE_RATE = 16000 // Default ASR sample rate (Hz)
  
  /**
   * Text Rewrite Configuration
   */
  const val EDGEAI_REWRITE_TEMPERATURE = 0.7f // Temperature for text rewrites
  const val EDGEAI_REWRITE_MAX_TOKENS = 512 // Max tokens for rewrite responses
  const val EDGEAI_REWRITE_HISTORY_LIMIT = 10 // Number of history messages to include
  const val EDGEAI_REWRITE_TIMEOUT_MS = 15000L // Timeout for rewrite operations
}
