package com.mtkresearch.breeze.rainbow

/**
 * Animation state enumeration for rainbow border animation
 */
enum class AnimationStateType {
    /** Default state with normal speed and brightness */
    IDLE,
    
    /** Enhanced state with faster speed and increased brightness */
    TYPING
}

/**
 * Represents the current state of the rainbow animation
 */
data class AnimationState(
    /** Current animation mode */
    val type: AnimationStateType = AnimationStateType.IDLE,
    
    /** Current rotation angle in degrees (0-360) */
    val rotation: Float = 0f,
    
    /** Current brightness multiplier (1.0 for idle, 1.3 for typing) */
    val brightness: Float = 1.0f,
    
    /** Current animation cycle duration in milliseconds */
    val cycleSpeed: Long = AnimationConfig.IDLE_CYCLE_DURATION
) {
    init {
        require(rotation in 0.0f..360.0f) { "Rotation must be between 0.0 and 360.0 degrees" }
        require(brightness in 0.5f..2.0f) { "Brightness must be between 0.5 and 2.0" }
        require(cycleSpeed in 500L..10000L) { "Cycle speed must be between 500 and 10000 milliseconds" }
    }
    
    /**
     * Creates a state for typing mode
     */
    fun toTyping(): AnimationState = copy(
        type = AnimationStateType.TYPING,
        brightness = AnimationConfig.BRIGHTNESS_MULTIPLIER,
        cycleSpeed = AnimationConfig.TYPING_CYCLE_DURATION
    )
    
    /**
     * Creates a state for idle mode
     */
    fun toIdle(): AnimationState = copy(
        type = AnimationStateType.IDLE,
        brightness = 1.0f,
        cycleSpeed = AnimationConfig.IDLE_CYCLE_DURATION
    )
    
    /**
     * Creates a state with updated rotation
     */
    fun withRotation(newRotation: Float): AnimationState = copy(
        rotation = newRotation % 360f
    )
}