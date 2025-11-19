package com.mtkresearch.securesms.breeze

import org.thoughtcrime.securesms.BuildConfig

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
   * Debug Configuration (only in debug builds)
   */
  val DEBUG_OVERLAY_ENABLED = BuildConfig.DEBUG
  val DEBUG_LOGGING_ENABLED = BuildConfig.DEBUG

  /**
   * Session Management
   */
  const val MAX_SESSIONS_PER_APP = 3
  const val MAX_SUGGESTIONS_PER_SESSION = 10
  const val SESSION_MEMORY_LIMIT_MB = 2L
}
