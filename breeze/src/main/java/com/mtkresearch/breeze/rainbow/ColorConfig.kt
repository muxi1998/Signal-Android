package com.mtkresearch.breeze.rainbow

/**
 * ROYGBIV color spectrum configuration for rainbow animation
 */
object ColorConfig {
    /**
     * Vibrant ROYGBIV spectrum colors with high saturation for magical AI effect
     */
    val ROYGBIV_SPECTRUM = intArrayOf(
        0xFFFF0000.toInt(), // Vibrant Red
        0xFFFF8000.toInt(), // Vibrant Orange
        0xFFFFFF00.toInt(), // Vibrant Yellow
        0xFF00FF00.toInt(), // Vibrant Green
        0xFF0000FF.toInt(), // Vibrant Blue
        0xFF4B0082.toInt(), // Vibrant Indigo
        0xFF8B00FF.toInt(), // Vibrant Violet
        0xFFFF0000.toInt()  // Back to Vibrant Red (seamless loop)
    )
    
    /**
     * Positions for gradient colors (evenly spaced)
     */
    val COLOR_POSITIONS = floatArrayOf(
        0.0f,      // Red
        0.143f,    // Orange (1/7)
        0.286f,    // Yellow (2/7) 
        0.429f,    // Green (3/7)
        0.571f,    // Blue (4/7)
        0.714f,    // Indigo (5/7)
        0.857f,    // Violet (6/7)
        1.0f       // Red again (seamless loop)
    )
    
    /**
     * Alpha value for rainbow colors (full opacity)
     */
    const val RAINBOW_ALPHA = 255
    
    /**
     * Brightness blend ratio for typing enhancement (40% for more vibrant AI effect)
     */
    const val TYPING_BRIGHTNESS_BLEND = 0.4f
}