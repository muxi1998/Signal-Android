package com.mtkresearch.breeze

import android.content.Context
import com.mtkresearch.breeze.api.BreezeRegistry

/**
 * Entry point for Breeze AI module initialization.
 * Called when the breezePlay flavor is active.
 */
object BreezeEntry {
  
  @Volatile
  private var initialized = false
  
  /**
   * Initialize Breeze AI components and register with the global registry.
   * This should be called from the Application class onCreate.
   */
  @JvmStatic
  fun initialize(context: Context) {
    if (initialized) {
      return
    }
    
    synchronized(this) {
      if (!initialized) {
        // Register UI hook implementation
        val uiHook = BreezeUiHookImpl(context.applicationContext)
        BreezeRegistry.register(
          dataProvider = BreezeRegistry.dataProvider, // Keep existing data provider from app
          uiHook = uiHook
        )
        
        initialized = true
      }
    }
  }
  
  /**
   * Check if Breeze is initialized.
   */
  @JvmStatic
  fun isInitialized(): Boolean = initialized
}
