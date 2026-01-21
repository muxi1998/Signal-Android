package com.mtkresearch.breeze

/**
 * Configuration object for Breeze AI.
 *
 * Currently minimal for build compatibility.
 */
object BreezeConfig {

  /**
   * Master feature flag for Breeze AI Assistant.
   * When false, all Breeze functionality is disabled.
   */
  const val FEATURE_ENABLED = true // Set to false to disable entirely
  
  // Demo Mode Configuration (kept for potential future usage or referenced by EdgeAI if any)
  const val USE_DEMO_MODE = false
}
