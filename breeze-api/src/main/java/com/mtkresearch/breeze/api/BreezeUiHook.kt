package com.mtkresearch.breeze.api

import android.content.Context
import android.graphics.Rect
import android.view.View

/**
 * Interface for Breeze UI integration hooks.
 * Allows the Breeze module to inject UI components into the app.
 */
interface BreezeUiHook {
  /**
   * Show the Breeze spark icon when input field is focused.
   * @param context Android context
   * @param inputView The input EditText view
   * @param inputBounds Bounds of the input field
   * @param inputText Current text in the input field
   * @param threadId Conversation thread ID
   */
  fun showSparkIcon(
    context: Context,
    inputView: View,
    inputBounds: Rect,
    inputText: String,
    threadId: Long
  )

  /**
   * Hide the Breeze spark icon.
   */
  fun hideContextualIcons()

  /**
   * Handle tap on contextual icon.
   */
  fun onContextualIconTapped(inputBounds: Rect, inputText: String)


  /**
   * Hide all Breeze UI elements.
   */
  fun hideAll()

  /**
   * Set callback for text injection into compose field.
   * @param callback Function that receives text to inject
   */
  fun setTextInjectionCallback(callback: (String) -> Unit)

  /**
   * Set callback for retrieving current input text.
   * @param callback Function that returns current text
   */
  fun setTextRetrievalCallback(callback: () -> String)

  /**
   * Set callback for triggering rainbow animation.
   * @param callback Function to trigger animation
   */
  fun setRainbowAnimationCallback(callback: () -> Unit)

  /**
   * Clear all callbacks.
   */
  fun clearCallbacks()

  /**
   * Cleanup and release resources.
   */
  fun cleanup()
}
