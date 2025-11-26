package com.mtkresearch.breeze.rainbow

/**
 * Configuration constants for rainbow animation behavior
 */
object AnimationConfig {
    // Animation timing constants
    const val IDLE_CYCLE_DURATION = 3000L     // 3 seconds per cycle
    const val TYPING_CYCLE_DURATION = 1500L   // 1.5 seconds when typing
    const val TYPING_TIMEOUT = 2000L          // Return to idle after 2s
    
    // Visual properties
    const val BRIGHTNESS_MULTIPLIER = 1.4f    // 40% brighter for typing (enhanced for vibrant colors)
    const val BORDER_WIDTH_DP = 2f            // 2dp border width
    
    // Performance thresholds
    const val TARGET_FPS_HIGH = 60            // High performance target
    const val TARGET_FPS_MEDIUM = 30          // Medium performance target
    const val TARGET_FPS_LOW = 15             // Low performance target
    const val MAX_FRAME_DROP_THRESHOLD = 10   // Maximum dropped frames before degradation
    const val POWER_SAVE_FPS_LIMIT = 15       // FPS limit in power save mode
}