package com.mtkresearch.breeze

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import com.mtkresearch.breeze.api.BreezeUiHook

/**
 * Implementation of BreezeUiHook that wraps the existing BreezeManager.
 * This adapter allows the BreezeManager to be accessed through the registry pattern.
 */
class BreezeUiHookImpl(private val context: Context) : BreezeUiHook {

  private var manager: BreezeManager? = null

  private fun getManager(): BreezeManager {
    if (manager == null) {
      manager = BreezeManager.getInstance(context)
    }
    return manager!!
  }

  override fun onContextualIconTapped(inputBounds: Rect, inputText: String) {
    getManager().onContextualIconTapped(inputBounds, inputText)
  }
  
  override fun hideAll() {
    getManager().hideAll()
  }
  
  override fun setTextInjectionCallback(callback: (String) -> Unit) {
    getManager().setTextInjectionCallback(callback)
  }
  
  override fun setTextRetrievalCallback(callback: () -> String) {
    getManager().setTextRetrievalCallback(callback)
  }
  
  override fun setRainbowAnimationCallback(callback: () -> Unit) {
    getManager().setRainbowAnimationCallback(callback)
  }

  override fun setVoiceInputCallback(callback: () -> Unit) {
    getManager().setVoiceInputCallback(callback)
  }

  override fun setFocusInputCallback(callback: () -> Unit) {
    getManager().setFocusInputCallback(callback)
  }

  override fun onVoiceInputComplete(inputBounds: Rect, transcribedText: String) {
    getManager().onVoiceInputComplete(inputBounds, transcribedText)
  }

  override fun setCurrentActivity(activity: Activity?) {
    getManager().setCurrentActivity(activity)
  }

  override fun clearCallbacks() {
    getManager().clearTextInjectionCallback()
    getManager().clearTextRetrievalCallback()
    getManager().clearRainbowAnimationCallback()
    getManager().clearVoiceInputCallback()
    getManager().clearFocusInputCallback()
  }

  override fun cleanup() {
    manager?.cleanup()
    manager = null
  }
}
