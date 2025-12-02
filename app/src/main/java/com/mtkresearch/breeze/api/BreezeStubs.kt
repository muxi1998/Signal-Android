package com.mtkresearch.breeze.api

/**
 * Stub BreezeDataProvider – no methods needed for non‑breeze builds.
 */
interface BreezeDataProvider

/**
 * Simple data classes used by the UI hook.
 */
data class ConversationSummary(val id: Long, val title: String?, val lastPreview: String?)

data class MessageSummary(val id: Long, val body: String?, val sender: String?, val timestamp: Long)



/**
 * Registry used by the app to obtain Breeze components.
 * In non‑breeze builds this is a no‑op placeholder.
 */
object BreezeRegistry {
    @Volatile var dataProvider: BreezeDataProvider? = null
        private set
    @Volatile var uiHook: BreezeUiHook? = null
        private set

    @JvmStatic
    fun register(dataProvider: BreezeDataProvider?, uiHook: BreezeUiHook?) {
        this.dataProvider = dataProvider
        this.uiHook = uiHook
    }

    @JvmStatic
    fun clear() {
        dataProvider = null
        uiHook = null
    }
}
