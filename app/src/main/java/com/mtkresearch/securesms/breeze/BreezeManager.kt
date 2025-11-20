package com.mtkresearch.securesms.breeze

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import com.mtkresearch.securesms.breeze.ui.BreezeFloatingWindow
import com.mtkresearch.securesms.edgeai.EdgeAI
import com.mtkresearch.securesms.edgeai.usecases.HistoryInJSON

/**
 * Simplified main manager for Breeze AI Floating Assistant.
 *
 * Consolidates all Breeze functionality into a single clean interface.
 * Follows the enhanced specification for visual style and behavior.
 */
class BreezeManager private constructor(
  private val context: Context
) {

  companion object {
    private val TAG = Log.tag(BreezeManager::class.java)

    @Volatile
    private var INSTANCE: BreezeManager? = null

    fun getInstance(context: Context): BreezeManager {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: BreezeManager(context.applicationContext).also { INSTANCE = it }
      }
    }
  }

  private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
  private val windowPreferences = WindowPreferences(context)
  private var floatingWindow: BreezeFloatingWindow? = null
  private var sparkIcon: SparkIcon? = null

  // AI state  
  private var currentSession: AISession? = null
  private var previousSummary: String = ""
  private var currentThreadId: Long? = null
  private var originalInputText: String = "" // Track original user input for History JSON
  
  // Text injection callback - set by ConversationFragment
  private var textInjectionCallback: ((String) -> Unit)? = null
  
  // Rainbow animation callback - set by ConversationFragment to trigger rainbow after AI injection
  private var rainbowAnimationCallback: (() -> Unit)? = null
  
  // App backgrounding detection per spec (Line 218)
  private val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {
      // Close window when app goes to background
      hideAll()
      Log.d(TAG, "App backgrounded - closing floating window")
    }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
  }
  
  init {
    // Register for app lifecycle events
    if (context is Application) {
      context.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
    }
  }

  /**
   * Show spark icon when input field is focused.
   * Called from ConversationFragment.
   */
  fun showSparkIcon(inputBounds: Rect, inputText: String, threadId: Long) {
    scope.launch {
      try {
        // Store thread ID for AI session
        currentThreadId = threadId

        Log.d(TAG, "===============================")
        Log.d(TAG, "showSparkIcon called with:")
        Log.d(TAG, "  bounds: $inputBounds")
        Log.d(TAG, "  text: '$inputText'")
        Log.d(TAG, "  threadId: $threadId")
        Log.d(TAG, "===============================")

        hideFloatingWindow()
        Log.d(TAG, "Floating window hidden")

        sparkIcon?.remove()
        Log.d(TAG, "Previous spark icon removed")

        Log.d(TAG, "Creating new SparkIcon...")
        sparkIcon = SparkIcon.create(context, inputBounds) {
          Log.d(TAG, "SparkIcon tapped!")
          onSparkIconTapped(inputBounds, inputText)
        }
        Log.d(TAG, "SparkIcon created: ${sparkIcon != null}")

        Log.d(TAG, "Calling sparkIcon.show()...")
        sparkIcon?.show()

        Log.d(TAG, "Spark icon creation completed successfully")
      } catch (e: Exception) {
        Log.e(TAG, "Error showing spark icon", e)
        Log.e(TAG, "Exception details:", e)
      }
    }
  }

  /**
   * Hide spark icon when input field loses focus.
   */
  fun hideSparkIcon() {
    scope.launch {
      sparkIcon?.remove()
      sparkIcon = null
    }
  }

  /**
   * Set text injection callback for communication with ConversationFragment.
   * This allows BreezeManager to inject AI suggestions into the compose text field.
   */
  fun setTextInjectionCallback(callback: (String) -> Unit) {
    textInjectionCallback = callback
    Log.d(TAG, "Text injection callback registered")
  }
  
  /**
   * Set rainbow animation callback for triggering rainbow animation after AI text injection.
   * This allows BreezeManager to trigger rainbow animation on the EditText after injection.
   */
  fun setRainbowAnimationCallback(callback: () -> Unit) {
    rainbowAnimationCallback = callback
    Log.d(TAG, "Rainbow animation callback registered")
  }
  
  /**
   * Clear text injection callback when ConversationFragment is destroyed.
   */
  fun clearTextInjectionCallback() {
    textInjectionCallback = null
    Log.d(TAG, "Text injection callback cleared")
  }
  
  /**
   * Clear rainbow animation callback when ConversationFragment is destroyed.
   */
  fun clearRainbowAnimationCallback() {
    rainbowAnimationCallback = null
    Log.d(TAG, "Rainbow animation callback cleared")
  }

  /**
   * Hide floating window and spark icon.
   */
  fun hideAll() {
    scope.launch {
      hideFloatingWindow()
      hideSparkIcon()
      currentSession = null
    }
  }

  private fun onSparkIconTapped(inputBounds: Rect, inputText: String) {
    scope.launch {
      try {
        // Store original input text for History JSON feature
        originalInputText = inputText
        
        // Create new AI session with thread ID
        currentSession = AISession.create(inputText, previousSummary, currentThreadId)

        // Hide spark icon
        hideSparkIcon()

        // Show floating window with saved size/position
        var windowSettings = windowPreferences.getWindowSettings()
        
        // Validate saved settings and reset if corrupted
        val displayMetrics = context.resources.displayMetrics
        if (windowSettings.height > displayMetrics.heightPixels * 0.6 || 
            windowSettings.width > displayMetrics.widthPixels) {
          Log.w(TAG, "Invalid saved window dimensions: ${windowSettings.width}x${windowSettings.height}, resetting")
          windowPreferences.clearPreferences()
          windowSettings = windowPreferences.getWindowSettings()
        }
        
        floatingWindow = BreezeFloatingWindow.create(
          context = context,
          anchorBounds = inputBounds,
          savedSettings = windowSettings,
          session = currentSession!!,
          onAccept = ::onAcceptSuggestion,
          onDismiss = ::onDismissSuggestion,
          onResize = windowPreferences::saveWindowSettings,
          onMove = windowPreferences::saveWindowPosition,
          onToneChange = ::onToneChipTapped
        )

        floatingWindow?.show()
        Log.d(TAG, "Floating window shown")
      } catch (e: Exception) {
        Log.e(TAG, "Error handling spark icon tap", e)
      }
    }
  }

  private fun onAcceptSuggestion() {
    scope.launch {
      currentSession?.let { session ->
        val textToInject = session.currentSuggestion
        Log.d(TAG, "Accepting suggestion: '$textToInject'")
        
        // Inject text using callback to ConversationFragment
        textInjectionCallback?.let { callback ->
          Log.d(TAG, "Injecting text via callback...")
          callback(textToInject)
          
          // IMPORTANT: Trigger rainbow animation ONLY after AI text injection
          rainbowAnimationCallback?.let { rainbowCallback ->
            Log.d(TAG, "Triggering rainbow animation after AI text injection")
            rainbowCallback()
          }
          
          // Move current suggestion to previous summary
          previousSummary = session.currentSuggestion
        } ?: run {
          Log.w(TAG, "No text injection callback available! Cannot inject text: '$textToInject'")
        }
      } ?: run {
        Log.w(TAG, "No current session available for text injection")
      }
      
      hideFloatingWindow()
    }
  }

  private fun onDismissSuggestion() {
    scope.launch {
      hideFloatingWindow()
      currentSession = null
    }
  }

  private fun onToneChipTapped(toneType: ToneType) {
    scope.launch {
      currentSession?.let { session ->
        // Move current to previous
        previousSummary = session.currentSuggestion

        // Generate new suggestion with tone, using original input text for History JSON
        session.applyTone(toneType, originalInputText)

        // Update window
        floatingWindow?.updateSession(session)
      }
    }
  }

  private fun hideFloatingWindow() {
    floatingWindow?.remove()
    floatingWindow = null
  }

  fun cleanup() {
    scope.launch {
      hideAll()
    }
    
    // Clear callbacks
    clearTextInjectionCallback()
    clearRainbowAnimationCallback()
    
    // Unregister lifecycle callbacks
    if (context is Application) {
      context.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks)
    }
  }
}

/**
 * AI Session that leverages EdgeAI usecases.
 * Currently uses "Make History in JSON" mock AI feature.
 */
class AISession private constructor(
  private val originalText: String,
  private var previousText: String,
  private val threadId: Long? = null
) {

  companion object {
    fun create(text: String, previous: String, threadId: Long? = null): AISession {
      val session = AISession(text, previous, threadId)
      session.generateInitialSuggestion()
      return session
    }
  }

  var currentSuggestion: String = ""
    private set

  var previousSummary: String = previousText
    private set

  private fun generateInitialSuggestion() {
    currentSuggestion = if (originalText.isBlank()) {
      "AI refined your text."
    } else {
      "AI refined your text."
    }
  }

  fun applyTone(toneType: ToneType, currentInputText: String = originalText) {
    // Move current to previous
    if (currentSuggestion.isNotBlank()) {
      previousSummary = currentSuggestion
    }

    // Generate new suggestion based on tone with optional context awareness
    val contextPrefix = if (threadId != null) "[Context+${toneType}] " else "[${toneType}] "
    
    currentSuggestion = when (toneType) {
      ToneType.FORMAL -> "${contextPrefix}AI rewrote your message formally."
      ToneType.FRIENDLY -> "${contextPrefix}Here's a friendlier version."
      ToneType.CLARITY -> "${contextPrefix}Improved clarity."
      ToneType.SHORTEN -> "${contextPrefix}Condensed version."
      ToneType.EXPAND -> "${contextPrefix}Added detail."
      ToneType.HISTORY_JSON -> {
        // Use EdgeAI "Make History in JSON" usecase for this preset
        if (threadId != null) {
          try {
            val request = HistoryInJSON.Request(
              inputText = currentInputText,
              threadId = threadId
            )
            EdgeAI.executeUsecase("history_in_json", request)
          } catch (e: Exception) {
            "${contextPrefix}History JSON formatting failed."
          }
        } else {
          "${contextPrefix}History not available."
        }
      }
    }
  }
}

/**
 * Tone types from the enhanced spec.
 */
enum class ToneType {
  HISTORY_JSON, FORMAL, FRIENDLY, CLARITY, SHORTEN, EXPAND
}
