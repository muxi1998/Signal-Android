package com.mtkresearch.breeze.api

/**
 * Central registry for Breeze AI components.
 * Provides dependency injection mechanism for loose coupling between app and Breeze modules.
 */
object BreezeRegistry {
  @Volatile
  var dataProvider: BreezeDataProvider? = null
    private set

  @Volatile
  var uiHook: BreezeUiHook? = null
    private set

  /**
   * Register Breeze components.
   * Should be called during application initialization.
   */
  @JvmStatic
  fun register(dataProvider: BreezeDataProvider?, uiHook: BreezeUiHook?) {
    this.dataProvider = dataProvider
    this.uiHook = uiHook
  }

  /**
   * Clear all registered components.
   * Useful for testing or cleanup.
   */
  @JvmStatic
  fun clear() {
    dataProvider = null
    uiHook = null
  }
}
