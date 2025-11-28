package org.thoughtcrime.securesms

import android.app.Application
import com.mtkresearch.breeze.BreezeEntry
import com.mtkresearch.breeze.api.BreezeRegistry
import com.mtkresearch.breeze.edgeai.EdgeAI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.breeze.AppBreezeDataProvider

/**
 * Breeze AI initialization for breezePlay flavor.
 * This file is only included in the breezePlay build variant.
 */
object BreezePlayInitializer {
  
  private val TAG = Log.tag(BreezePlayInitializer::class.java)
  private val initScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
  
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
      
      // Initialize Breeze module (registers UI hook)
      BreezeEntry.initialize(application)
      
      // Initialize EdgeAI SDK for AI features
      initScope.launch {
        try {
          Log.i(TAG, "Initializing EdgeAI SDK...")
          EdgeAI.initialize(application.applicationContext)
          Log.i(TAG, "EdgeAI SDK initialized successfully - ready for AI features")
        } catch (e: Exception) {
          Log.e(TAG, "Failed to initialize EdgeAI SDK - AI features will not work", e)
          Log.e(TAG, "Make sure BreezeApp Engine is installed on the device")
        }
      }
      
      Log.i(TAG, "Breeze AI initialized successfully")
      Log.i(TAG, "  DataProvider: ${BreezeRegistry.dataProvider != null}")
      Log.i(TAG, "  UiHook: ${BreezeRegistry.uiHook != null}")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize Breeze AI", e)
    }
  }
}
