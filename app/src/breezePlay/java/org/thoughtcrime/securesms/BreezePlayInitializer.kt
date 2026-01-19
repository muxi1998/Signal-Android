package org.thoughtcrime.securesms

import android.app.Application
import com.mtkresearch.breeze.BreezeEntry
import com.mtkresearch.breeze.api.BreezeRegistry
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.breeze.AppBreezeDataProvider

/**
 * Breeze AI initialization for breezePlay flavor.
 * This file is only included in the breezePlay build variant.
 * 
 * Following the TDD plan, EdgeAI SDK initialization will be added
 * when the test infrastructure is ready.
 */
object BreezePlayInitializer {
  
  private val TAG = Log.tag(BreezePlayInitializer::class.java)
  
  /**
   * Initialize Breeze AI components for the breezePlay flavor.
   * Should be called from ApplicationContext.onCreate()
   */
  @JvmStatic
  fun initialize(application: Application) {
    try {
      Log.i(TAG, "Initializing Breeze AI for breezePlay flavor...")
      
      // Register app-side data provider with application context
      val dataProvider = AppBreezeDataProvider(application.applicationContext)
      BreezeRegistry.register(dataProvider, null)
      
      // Initialize Breeze module
      BreezeEntry.initialize(application)
      
      // TODO: EdgeAI SDK initialization will be added following TDD plan
      // When ready, use official SDK directly:
      // import com.mtkresearch.breezeapp.edgeai.EdgeAI
      // EdgeAI.initializeAndWait(application.applicationContext)
      
      Log.i(TAG, "Breeze AI initialized successfully")
      Log.i(TAG, "  DataProvider: ${BreezeRegistry.dataProvider != null}")
      Log.i(TAG, "  UiHook: ${BreezeRegistry.uiHook != null}")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize Breeze AI", e)
    }
  }
}

