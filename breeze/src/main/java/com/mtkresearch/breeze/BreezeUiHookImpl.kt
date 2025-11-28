package com.mtkresearch.breeze

import android.content.Context
import android.graphics.Rect
import android.view.View
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
  
  override fun showSparkIcon(
    context: Context,
    inputView: View,
    inputBounds: Rect,
    inputText: String,
    threadId: Long,
    onTapped: () -> Unit
  ) {
    getManager().showSparkIcon(inputBounds, inputText, threadId)
  }
  
  override fun showRainbowRobotIcon(
    context: Context,
    robotButton: View,
    threadId: Long,
    onTapped: () -> Unit
  ) {
    getManager().showRainbowRobotIcon(robotButton, threadId, onTapped)
  }
  
  override fun hideContextualIcons() {
    getManager().hideContextualIcons()
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
  
  override fun clearCallbacks() {
    getManager().clearTextInjectionCallback()
    getManager().clearTextRetrievalCallback()
    getManager().clearRainbowAnimationCallback()
  }
  
  override fun cleanup() {
    manager?.cleanup()
    manager = null
  }
}
