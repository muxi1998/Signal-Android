package com.mtkresearch.breeze.api

import android.app.Activity
import android.graphics.Rect

/**
 * Interface for Breeze UI integration hooks.
 * Allows the Breeze module to handle AI assistant interactions.
 *
 * Note: Contextual icons (rainbow pen/mic) are now embedded in the input panel
 * and managed by ConversationFragment. This interface handles the actions
 * triggered by those icons.
 */
interface BreezeUiHook {

  /**
   * Handle tap on contextual icon (rainbow pen or rainbow mic).
   * @param inputBounds Bounds of the input field for positioning floating window
   * @param inputText Current text in the input field (empty for mic icon)
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
   * Set callback for triggering voice input (ASR).
   * Called when user selects voice option from input choice popup.
   * @param callback Function that triggers ASR and returns transcribed text via onVoiceInputComplete
   */
  fun setVoiceInputCallback(callback: () -> Unit)

  /**
   * Set callback for focusing text input.
   * Called when user selects text option from input choice popup.
   * @param callback Function that focuses the compose text field
   */
  fun setFocusInputCallback(callback: () -> Unit)

  /**
   * Called when voice input (ASR) completes with transcribed text.
   * This triggers the floating window to show with the transcribed text.
   * @param inputBounds Bounds of the input field
   * @param transcribedText The text transcribed from voice
   */
  fun onVoiceInputComplete(inputBounds: Rect, transcribedText: String)

  /**
   * Set the current Activity for showing popups.
   * Must be called when the hosting Activity becomes active.
   * @param activity The current Activity, or null when destroyed
   */
  fun setCurrentActivity(activity: Activity?)

  /**
   * Clear all callbacks.
   */
  fun clearCallbacks()

  /**
   * Cleanup and release resources.
   */
  fun cleanup()
}
