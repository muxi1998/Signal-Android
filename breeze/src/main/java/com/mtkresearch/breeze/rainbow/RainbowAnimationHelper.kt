package com.mtkresearch.breeze.rainbow

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.lifecycle.LifecycleOwner
import org.signal.core.util.logging.Log

/**
 * Helper class for managing rainbow border animation on views.
 * Adapted for Breeze AI - handles lifecycle management, typing detection, and animation state transitions.
 * Modified to support AI text injection detection and work with any View (including InputPanel).
 */
class RainbowAnimationHelper(
    private val targetView: View,
    private val lifecycleOwner: LifecycleOwner
) {
    
    private val TAG = Log.tag(RainbowAnimationHelper::class.java)
    private var gradientController: RainbowBorderController? = null
    private var textWatcher: TextWatcher? = null
    private var isAITextInjection = false
    
    /**
     * Initializes the rainbow animation on the target view for manual typing detection
     */
    fun initialize() {
        try {
            Log.d(TAG, "Initializing rainbow animation for typing detection")
            
            // Create and attach the rainbow border controller
            gradientController = RainbowBorderController(targetView, lifecycleOwner).apply {
                start()
            }
            
            // Set up text watching for typing detection
            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    // Not needed
                }
                
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // Only animate for manual typing - NOT for AI text injection
                    if (!isAITextInjection && (count > 0 || before > 0)) {
                        Log.d(TAG, "Manual typing detected, switching to typing animation")
                        gradientController?.switchToTyping()
                    }
                }
                
                override fun afterTextChanged(s: Editable?) {
                    // Reset AI injection flag after text change
                    isAITextInjection = false
                }
            }
            
            // Only add text watcher if target is an EditText
            (targetView as? EditText)?.addTextChangedListener(textWatcher)
            Log.d(TAG, "Rainbow animation initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing rainbow animation", e)
        }
    }
    
    /**
     * Starts persistent rainbow animation specifically for AI text injection
     * This shows the rainbow immediately and persists until manually stopped
     */
    fun startAITextInjectionAnimation() {
        try {
            Log.d(TAG, "Starting persistent rainbow animation for AI text injection")
            isAITextInjection = true
            
            // Create controller if not exists
            if (gradientController == null) {
                gradientController = RainbowBorderController(targetView, lifecycleOwner)
            }
            
            // Force start the rainbow animation for AI injection - use typing mode for enhanced effect
            gradientController?.start()
            gradientController?.switchToTyping() // Use faster, brighter typing mode for AI
            
            Log.d(TAG, "Persistent AI text injection rainbow animation started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AI text injection rainbow animation", e)
        }
    }
    
    /**
     * Stops the rainbow animation (typically after AI text is accepted)
     */
    fun stopAITextInjectionAnimation() {
        try {
            Log.d(TAG, "Stopping rainbow animation for AI text injection")
            gradientController?.switchToIdle()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AI text injection rainbow animation", e)
        }
    }
    
    /**
     * Cleans up the rainbow animation and removes listeners
     */
    fun cleanup() {
        try {
            val watcher = textWatcher
            if (watcher != null) {
                (targetView as? EditText)?.removeTextChangedListener(watcher)
            }
            textWatcher = null
            
            gradientController?.cleanup()
            gradientController = null
            
            Log.d(TAG, "Rainbow animation cleaned up successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up rainbow animation", e)
        }
    }
    
    /**
     * Updates animation for theme changes
     */
    fun updateForTheme(isDarkTheme: Boolean) {
        gradientController?.updateForTheme(isDarkTheme)
    }
    
    /**
     * Gets the current animation state
     */
    fun getAnimationState(): AnimationStateType? {
        return gradientController?.getAnimationState()
    }
    
    /**
     * Sets a callback for animation state changes
     */
    fun setStateChangeCallback(callback: (AnimationStateType) -> Unit) {
        gradientController?.setStateChangeCallback(callback)
    }
    
    /**
     * Triggers sparkle effect (call when message is sent)
     */
    fun triggerSparkleEffect() {
        gradientController?.triggerSparkleEffect()
    }
    
    companion object {
        /**
         * Static helper to apply persistent rainbow animation after AI text injection
         * This is called from BreezeManager when AI suggestions are accepted
         * Animation persists until manually stopped (no automatic timeout)
         */
        fun applyRainbowAfterAIInjection(targetView: View, lifecycleOwner: LifecycleOwner, duration: Long = 0L): RainbowAnimationHelper? {
            return try {
                Log.d("RainbowAnimationHelper", "Applying persistent rainbow animation after AI text injection to: ${targetView::class.simpleName}")
                
                val helper = RainbowAnimationHelper(targetView, lifecycleOwner)
                helper.startAITextInjectionAnimation()
                
                // Return helper instance for manual control (no auto-stop)
                helper
                
            } catch (e: Exception) {
                Log.e("RainbowAnimationHelper", "Error applying rainbow after AI injection", e)
                null
            }
        }
    }
}