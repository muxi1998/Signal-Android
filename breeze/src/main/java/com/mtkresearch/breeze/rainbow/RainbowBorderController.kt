package com.mtkresearch.breeze.rainbow

import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Controller that manages the lifecycle and state of the rainbow border animation.
 * Handles animation transitions, power management, and lifecycle awareness.
 */
class RainbowBorderController(
    private val view: View,
    private val lifecycleOwner: LifecycleOwner
) : DefaultLifecycleObserver {
    
    private val context: Context = view.context
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Animation components
    private val drawable = RainbowGradientDrawable(context)
    private val sparkleEffect = SparkleEffect(context)
    private var idleAnimator: ValueAnimator? = null
    private var typingAnimator: ValueAnimator? = null
    private var currentAnimator: ValueAnimator? = null
    
    // State management
    private var currentState = AnimationStateType.IDLE
    private var isAttached = false
    private var stateChangeCallback: ((AnimationStateType) -> Unit)? = null
    
    // Typing timeout handling
    private val returnToIdleRunnable = Runnable { switchToIdle() }
    
    init {
        setupAnimators()
        // Use foreground to overlay rainbow on existing background
        view.foreground = drawable
        lifecycleOwner.lifecycle.addObserver(this)
        isAttached = true
    }
    
    /**
     * Starts the animation controller
     */
    fun start() {
        if (shouldAnimate() && currentAnimator == null) {
            currentAnimator = idleAnimator
            currentAnimator?.start()
        }
    }
    
    /**
     * Stops the animation controller and cleans up resources
     */
    fun stop() {
        currentAnimator?.cancel()
        currentAnimator = null
    }
    
    /**
     * Pauses the animation (typically called on activity pause)
     */
    fun pause() {
        currentAnimator?.pause()
    }
    
    /**
     * Resumes the animation (typically called on activity resume)
     */
    fun resume() {
        if (shouldAnimate()) {
            currentAnimator?.resume()
        }
    }
    
    /**
     * Switches to typing animation mode
     */
    fun switchToTyping() {
        mainHandler.removeCallbacks(returnToIdleRunnable)
        
        if (currentState != AnimationStateType.TYPING) {
            currentState = AnimationStateType.TYPING
            
            currentAnimator?.cancel()
            currentAnimator = typingAnimator
            
            if (shouldAnimate()) {
                currentAnimator?.start()
            }
            
            stateChangeCallback?.invoke(AnimationStateType.TYPING)
        }
        
        // Schedule return to idle after timeout
        mainHandler.postDelayed(returnToIdleRunnable, AnimationConfig.TYPING_TIMEOUT)
    }
    
    /**
     * Switches to idle animation mode
     */
    fun switchToIdle() {
        mainHandler.removeCallbacks(returnToIdleRunnable)
        
        if (currentState != AnimationStateType.IDLE) {
            currentState = AnimationStateType.IDLE
            
            currentAnimator?.cancel()
            currentAnimator = idleAnimator
            
            if (shouldAnimate()) {
                currentAnimator?.start()
            }
            
            stateChangeCallback?.invoke(AnimationStateType.IDLE)
        }
    }
    
    /**
     * Checks if animation should be running based on system state
     */
    fun shouldAnimate(): Boolean {
        return isAttached &&
                !powerManager.isPowerSaveMode &&
                view.isAttachedToWindow &&
                view.visibility == View.VISIBLE
    }
    
    /**
     * Gets the current animation state
     */
    fun getAnimationState(): AnimationStateType = currentState
    
    /**
     * Sets a callback for animation state changes
     */
    fun setStateChangeCallback(callback: (AnimationStateType) -> Unit) {
        stateChangeCallback = callback
    }
    
    /**
     * Updates theme-aware colors
     */
    fun updateForTheme(isDarkTheme: Boolean) {
        drawable.updateForTheme(isDarkTheme)
    }
    
    /**
     * Triggers the sparkle effect (typically called when message is sent)
     */
    fun triggerSparkleEffect() {
        if (shouldAnimate()) {
            sparkleEffect.startSparkleAnimation()
        }
    }
    
    /**
     * Cleans up resources and removes lifecycle observer
     */
    fun cleanup() {
        stop()
        mainHandler.removeCallbacks(returnToIdleRunnable)
        lifecycleOwner.lifecycle.removeObserver(this)
        view.foreground = null
        isAttached = false
    }
    
    // Lifecycle observer methods
    override fun onResume(owner: LifecycleOwner) {
        resume()
    }
    
    override fun onPause(owner: LifecycleOwner) {
        pause()
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        cleanup()
    }
    
    private fun setupAnimators() {
        // Idle animation: 3-second cycle
        idleAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = AnimationConfig.IDLE_CYCLE_DURATION
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val rotation = animation.animatedValue as Float
                drawable.updateAnimation(rotation, AnimationStateType.IDLE)
            }
        }
        
        // Typing animation: 1.5-second cycle (faster)
        typingAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = AnimationConfig.TYPING_CYCLE_DURATION
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val rotation = animation.animatedValue as Float
                drawable.updateAnimation(rotation, AnimationStateType.TYPING)
            }
        }
        
        // Start with idle animation
        currentAnimator = idleAnimator
    }
}