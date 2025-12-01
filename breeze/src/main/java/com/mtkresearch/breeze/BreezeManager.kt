package com.mtkresearch.breeze

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import com.mtkresearch.breeze.ui.BreezeFloatingWindow
import com.mtkresearch.breeze.ui.BreezeInputChoicePopup
import com.mtkresearch.breeze.edgeai.EdgeAI
import com.mtkresearch.breeze.edgeai.usecases.HistoryInJSON
import com.mtkresearch.breeze.edgeai.usecases.ChatUseCase
import com.mtkresearch.breeze.edgeai.usecases.TextRewriteUseCase
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

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
  private var inputChoicePopup: BreezeInputChoicePopup? = null

  // AI state  
  private var currentSession: AISession? = null
  private var previousSummary: String = ""
  private var currentThreadId: Long? = null
  private var originalInputText: String = "" // Track original user input for History JSON
  
  // Text injection callback - set by ConversationFragment
  private var textInjectionCallback: ((String) -> Unit)? = null

  // Text retrieval callback - set by ConversationFragment to get current input text
  private var textRetrievalCallback: (() -> String)? = null

  // Rainbow animation callback - set by ConversationFragment to trigger rainbow after AI injection
  private var rainbowAnimationCallback: (() -> Unit)? = null

  // Voice input callback - set by ConversationFragment to trigger ASR
  private var voiceInputCallback: (() -> Unit)? = null

  // Focus input callback - set by ConversationFragment to focus the compose text field
  private var focusInputCallback: (() -> Unit)? = null

  // Store anchor bounds for voice input completion
  private var pendingVoiceInputBounds: Rect? = null

  // Activity reference for showing popups (set by ConversationFragment)
  private var currentActivity: Activity? = null

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
   * Handle contextual icon tap (rainbow pen or rainbow mic).
   * Shows the floating window or input choice popup.
   */
  fun onContextualIconTapped(inputBounds: Rect, inputText: String) {
    // Prevent multiple panels
    if (floatingWindow?.isShowing() == true || inputChoicePopup?.isShowing() == true) {
      return
    }

    scope.launch {
      try {
        // If input is empty, show choice popup (Voice vs Text)
        if (inputText.isEmpty()) {
          showInputChoicePopup(inputBounds)
          return@launch
        }

        // Store original input text for History JSON feature
        originalInputText = inputText
        
        // Create new AI session with thread ID
        currentSession = AISession.create(inputText, previousSummary, currentThreadId)

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
          onToneChange = ::onToneChipTapped,
          onChatMessage = { message -> onChatMessageReceived(message, inputBounds) }
        )

        floatingWindow?.show()

        // Add initial conversation context (user's text)
        floatingWindow?.addToConversation(isUser = true, inputText)

        Log.d(TAG, "Floating window shown from contextual icon tap")
      } catch (e: Exception) {
        Log.e(TAG, "Error handling contextual icon tap", e)
      }
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
   * Set text retrieval callback for getting current input text from ConversationFragment.
   * This allows BreezeManager to get the latest text when user clicks on tone options.
   */
  fun setTextRetrievalCallback(callback: () -> String) {
    textRetrievalCallback = callback
    Log.d(TAG, "Text retrieval callback registered")
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
   * Clear text retrieval callback when ConversationFragment is destroyed.
   */
  fun clearTextRetrievalCallback() {
    textRetrievalCallback = null
    Log.d(TAG, "Text retrieval callback cleared")
  }
  
  /**
   * Clear rainbow animation callback when ConversationFragment is destroyed.
   */
  fun clearRainbowAnimationCallback() {
    rainbowAnimationCallback = null
    Log.d(TAG, "Rainbow animation callback cleared")
  }

  /**
   * Set voice input callback for triggering ASR from ConversationFragment.
   */
  fun setVoiceInputCallback(callback: () -> Unit) {
    voiceInputCallback = callback
    Log.d(TAG, "Voice input callback registered")
  }

  /**
   * Set focus input callback for focusing the compose text field.
   */
  fun setFocusInputCallback(callback: () -> Unit) {
    focusInputCallback = callback
    Log.d(TAG, "Focus input callback registered")
  }

  /**
   * Clear voice input callback.
   */
  fun clearVoiceInputCallback() {
    voiceInputCallback = null
    Log.d(TAG, "Voice input callback cleared")
  }

  /**
   * Clear focus input callback.
   */
  fun clearFocusInputCallback() {
    focusInputCallback = null
    Log.d(TAG, "Focus input callback cleared")
  }

  /**
   * Set the current Activity for showing popups.
   * Must be called by ConversationFragment when it becomes active.
   */
  fun setCurrentActivity(activity: Activity?) {
    currentActivity = activity
    Log.d(TAG, "Current activity set: ${activity != null}")
  }

  /**
   * Set the current thread/conversation ID.
   * Must be called by ConversationFragment when thread changes.
   * Required for HistoryInJSON feature to fetch conversation history.
   */
  fun setCurrentThreadId(threadId: Long?) {
    currentThreadId = threadId
    Log.d(TAG, "Current thread ID set: $threadId")
  }

  /**
   * Called when voice input (ASR) completes with transcribed text.
   * Shows the floating window with the transcribed text.
   */
  fun onVoiceInputComplete(inputBounds: Rect, transcribedText: String) {
    if (transcribedText.isNotBlank()) {
      Log.d(TAG, "Voice input complete with text: '$transcribedText'")
      // Inject the transcribed text first
      textInjectionCallback?.invoke(transcribedText)
      // Then show the floating window
      onContextualIconTapped(inputBounds, transcribedText)
    } else {
      Log.d(TAG, "Voice input complete but text is empty, ignoring")
    }
  }

  /**
   * Hide floating window and dismiss any popups.
   */
  fun hideAll() {
    scope.launch {
      hideFloatingWindow()
      inputChoicePopup?.dismiss()
      inputChoicePopup = null
      currentSession = null
    }
  }

  private fun onAcceptSuggestion(draftText: String) {
    scope.launch {
      Log.d(TAG, "Accepting draft: '$draftText'")

      // Inject the draft text (possibly edited by user) to compose field
      textInjectionCallback?.let { callback ->
        Log.d(TAG, "Injecting draft via callback...")
        callback(draftText)

        // Trigger rainbow animation after AI text injection
        rainbowAnimationCallback?.let { rainbowCallback ->
          Log.d(TAG, "Triggering rainbow animation after AI text injection")
          rainbowCallback()
        }

        // Update previous summary
        previousSummary = draftText
      } ?: run {
        Log.w(TAG, "No text injection callback available! Cannot inject text: '$draftText'")
      }

      // Hide keyboard when accepting draft
      hideKeyboard()

      hideFloatingWindow()
    }
  }

  private fun hideKeyboard() {
    try {
      currentActivity?.let { activity ->
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        val currentFocus = activity.currentFocus
        if (currentFocus != null) {
          imm?.hideSoftInputFromWindow(currentFocus.windowToken, 0)
        } else {
          // Hide from the activity's window decor view
          activity.window?.decorView?.let { view ->
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
          }
        }
        Log.d(TAG, "Keyboard hidden")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error hiding keyboard", e)
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
        // Get current draft text from floating window (tone chips modify the draft)
        val currentDraft = floatingWindow?.getCurrentDraft() ?: ""
        val textToModify = if (currentDraft.isNotBlank()) currentDraft else originalInputText

        Log.d(TAG, "Tone chip tapped: $toneType, modifying text: '$textToModify'")

        // Add user action to conversation (showing what user asked Charles to do)
        val userAction = when (toneType) {
          ToneType.FORMAL -> "Make it more formal"
          ToneType.FRIENDLY -> "Make it friendlier"
          ToneType.CLARITY -> "Improve clarity"
          ToneType.SHORTEN -> "Make it shorter"
          ToneType.EXPAND -> "Expand with more detail"
          ToneType.HISTORY_JSON -> "Format with conversation history"
        }
        floatingWindow?.addToConversation(isUser = true, userAction)

        // Special handling for HISTORY_JSON - no AI involved
        if (toneType == ToneType.HISTORY_JSON) {
          handleHistoryInJsonTone(textToModify)
          return@launch
        }

        // Move current to previous
        previousSummary = session.currentSuggestion

        // Set streaming callback to update window in real-time
        session.setStreamingCallback { partialText ->
          floatingWindow?.updateStreamingText(partialText)
        }

        // Set completion callback to update conversation when streaming finishes
        session.setCompletionCallback { finalText ->
          Log.d(TAG, "Tone transformation completed: ${finalText.take(50)}...")
          // Update session and add Charles response to conversation with TONE type
          floatingWindow?.updateSession(session, BreezeFloatingWindow.ResponseType.TONE)
        }

        // Generate new suggestion with tone using streaming chat
        session.applyTone(toneType, textToModify)
      }
    }
  }

  /**
   * Handle HistoryInJson tone - directly call the usecase without AI.
   * This transforms the text by including conversation history in JSON format.
   */
  private fun handleHistoryInJsonTone(inputText: String) {
    val threadId = currentThreadId
    if (threadId == null) {
      Log.w(TAG, "No thread ID available for HistoryInJson")
      floatingWindow?.updateDraft("Error: No conversation context available")
      floatingWindow?.addCharlesResponse("I need a conversation context to format history in JSON.", BreezeFloatingWindow.ResponseType.SYSTEM)
      return
    }

    try {
      // Call HistoryInJSON usecase directly - no AI
      val jsonOutput = HistoryInJSON.execute(
        HistoryInJSON.Request(
          inputText = inputText,
          threadId = threadId,
          historyLimit = 10
        )
      )

      Log.d(TAG, "HistoryInJSON result: $jsonOutput")

      // Update draft with JSON output
      floatingWindow?.updateDraft(jsonOutput)

      // Add to conversation history with HISTORY response type
      floatingWindow?.addCharlesResponse("Here's your message with conversation history in JSON format.", BreezeFloatingWindow.ResponseType.HISTORY)
    } catch (e: Exception) {
      Log.e(TAG, "Error executing HistoryInJSON", e)
      floatingWindow?.updateDraft("Error: ${e.message}")
      floatingWindow?.addCharlesResponse("Sorry, I encountered an error formatting the history.", BreezeFloatingWindow.ResponseType.SYSTEM)
    }
  }

  private fun hideFloatingWindow() {
    floatingWindow?.remove()
    floatingWindow = null
  }

  private fun showInputChoicePopup(anchorBounds: Rect) {
    val activity = currentActivity
    if (activity == null) {
      Log.e(TAG, "Cannot show popup - no Activity available. Call setCurrentActivity first.")
      return
    }

    // Store bounds for voice input completion
    pendingVoiceInputBounds = anchorBounds

    inputChoicePopup = BreezeInputChoicePopup(
      activity = activity,
      anchorBounds = anchorBounds,
      onTextSubmit = { text ->
        Log.d(TAG, "Text submitted from popup: $text")
        handleTextInputSubmit(text, anchorBounds)
      },
      onRecordingComplete = { audioFile ->
        Log.d(TAG, "Recording complete: ${audioFile.absolutePath}")
        handleRecordingComplete(audioFile, anchorBounds)
      },
      onDismiss = {
        Log.d(TAG, "Input choice popup dismissed")
        inputChoicePopup = null
      }
    )

    inputChoicePopup?.show()
  }

  /**
   * Handle text input submission from the popup.
   * Calls streaming chat and shows the result in floating window as "Draft".
   * Does NOT inject to root input field - user is still discussing with Charles.
   */
  private fun handleTextInputSubmit(text: String, anchorBounds: Rect) {
    scope.launch {
      try {
        // Store original text (what user asked Charles)
        originalInputText = text

        // Create AI session - the response will be shown as "Draft" for further discussion
        currentSession = AISession.create(text, previousSummary, currentThreadId)

        // Start streaming chat
        currentSession?.let { session ->
          session.setStreamingCallback { partialText ->
            inputChoicePopup?.updateProcessingText("Charles: $partialText")
          }

          // Set completion callback to show floating window when streaming finishes
          session.setCompletionCallback { finalText ->
            Log.d(TAG, "Text input processing completed: ${finalText.take(50)}...")
            // Show completion and transition to floating window
            inputChoicePopup?.showAsrComplete {
              showFloatingWindowWithSession(anchorBounds)
            }
          }

          // Use chat() for conversational interactions (not tone transformation)
          session.chat(userMessage = text)
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error processing text input", e)
        inputChoicePopup?.dismiss()
      }
    }
  }

  /**
   * Handle recording completion. Process with ASR then LLM.
   * Does NOT inject to root input field - user is still discussing with Charles.
   */
  private fun handleRecordingComplete(audioFile: java.io.File, anchorBounds: Rect) {
    scope.launch {
      try {
        inputChoicePopup?.updateProcessingText("Transcribing audio...")

        // TODO: Implement actual ASR using EdgeAI
        // For now, simulate ASR completion after a delay
        delay(2000)

        // Simulated transcribed text (replace with actual ASR result)
        val transcribedText = "This is a simulated transcription. Replace with actual ASR."

        Log.d(TAG, "ASR complete: $transcribedText")
        inputChoicePopup?.updateProcessingText("Processing with AI...")

        // Store what user said (transcribed)
        originalInputText = transcribedText

        // Create AI session - response will be shown as "Draft" for further discussion
        currentSession = AISession.create(transcribedText, previousSummary, currentThreadId)

        currentSession?.let { session ->
          session.setStreamingCallback { partialText ->
            inputChoicePopup?.updateProcessingText("Charles: $partialText")
          }

          // Set completion callback to show floating window when streaming finishes
          session.setCompletionCallback { finalText ->
            Log.d(TAG, "Voice input processing completed: ${finalText.take(50)}...")
            // Show completion and transition to floating window
            inputChoicePopup?.showAsrComplete {
              showFloatingWindowWithSession(anchorBounds)
            }
            // Clean up audio file after processing
            audioFile.delete()
          }

          // Use chat() for conversational interactions (not tone transformation)
          session.chat(userMessage = transcribedText)
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error processing recording", e)
        inputChoicePopup?.dismiss()
      }
    }
  }

  /**
   * Show the floating window with the current session.
   */
  private fun showFloatingWindowWithSession(anchorBounds: Rect) {
    scope.launch {
      try {
        currentSession?.let { session ->
          var windowSettings = windowPreferences.getWindowSettings()

          val displayMetrics = context.resources.displayMetrics
          if (windowSettings.height > displayMetrics.heightPixels * 0.6 ||
            windowSettings.width > displayMetrics.widthPixels) {
            windowPreferences.clearPreferences()
            windowSettings = windowPreferences.getWindowSettings()
          }

          floatingWindow = BreezeFloatingWindow.create(
            context = context,
            anchorBounds = anchorBounds,
            savedSettings = windowSettings,
            session = session,
            onAccept = ::onAcceptSuggestion,
            onDismiss = ::onDismissSuggestion,
            onResize = windowPreferences::saveWindowSettings,
            onMove = windowPreferences::saveWindowPosition,
            onToneChange = ::onToneChipTapped,
            onChatMessage = { message -> onChatMessageReceived(message, anchorBounds) }
          )

          floatingWindow?.show()

          // Add initial conversation context with appropriate response type
          // Determine if input was from voice (ASR) or text
          val isVoiceInput = pendingVoiceInputBounds != null
          floatingWindow?.addToConversation(isUser = true, originalInputText)
          if (session.currentSuggestion.isNotBlank()) {
            val responseType = if (isVoiceInput) {
              BreezeFloatingWindow.ResponseType.ASR
            } else {
              BreezeFloatingWindow.ResponseType.LLM
            }
            floatingWindow?.addCharlesResponse("Here's my suggestion based on your input.", responseType)
          }

          Log.d(TAG, "Floating window shown after input choice (voice=$isVoiceInput)")
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error showing floating window", e)
      }
    }
  }

  /**
   * Handle chat message from the floating window.
   * User is continuing the conversation with Charles.
   *
   * The draft section holds the current most suitable draft.
   * When user discusses with Charles, the full conversation history is passed
   * along with the current draft and user's newest message, so LLM has full context.
   */
  private fun onChatMessageReceived(message: String, anchorBounds: Rect) {
    scope.launch {
      Log.d(TAG, "Chat message received: $message")

      currentSession?.let { session ->
        // Get the current draft from the floating window
        val currentDraft = floatingWindow?.getCurrentDraft() ?: ""

        // Get conversation history for context
        val conversationHistory = floatingWindow?.getConversationHistoryForLLM() ?: ""

        // Set streaming callback to update the draft field
        session.setStreamingCallback { partialText ->
          floatingWindow?.updateStreamingText(partialText)
        }

        // Set completion callback to update conversation when streaming finishes
        session.setCompletionCallback { finalText ->
          Log.d(TAG, "Chat response completed: ${finalText.take(50)}...")
          // Update session and add Charles response to conversation with LLM type
          floatingWindow?.updateSession(session, BreezeFloatingWindow.ResponseType.LLM)
        }

        Log.d(TAG, "Chat with Charles: message='$message', hasHistory=${conversationHistory.isNotBlank()}, hasDraft=${currentDraft.isNotBlank()}")

        // Use chat() for conversational follow-up (not tone transformation)
        // Pass context separately - ChatUseCase will format appropriately
        session.chat(
          userMessage = message,
          conversationHistory = conversationHistory.takeIf { it.isNotBlank() },
          currentDraft = currentDraft.takeIf { it.isNotBlank() }
        )
      }
    }
  }

  fun cleanup() {
    scope.launch {
      hideAll()
    }

    // Clear all callbacks
    clearTextInjectionCallback()
    clearTextRetrievalCallback()
    clearRainbowAnimationCallback()
    clearVoiceInputCallback()
    clearFocusInputCallback()
    pendingVoiceInputBounds = null

    // Unregister lifecycle callbacks
    if (context is Application) {
      context.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks)
    }
  }
}

/**
 * AI Session that leverages EdgeAI streaming chat for tone rewrites.
 */
class AISession private constructor(
  private val originalText: String,
  private var previousText: String,
  private val threadId: Long? = null
) {

  companion object {
    private val TAG = Log.tag(AISession::class.java)
    
    fun create(text: String, previous: String, threadId: Long? = null): AISession {
      val session = AISession(text, previous, threadId)
      session.generateInitialSuggestion()
      return session
    }
  }
  
  private val textRewriteUseCase = TextRewriteUseCase()
  private val chatUseCase = ChatUseCase()
  private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

  var currentSuggestion: String = ""
    private set

  var previousSummary: String = previousText
    private set
    
  var isStreaming: Boolean = false
    private set
  
  private var streamingCallback: ((String) -> Unit)? = null
  private var completionCallback: ((String) -> Unit)? = null

  private fun generateInitialSuggestion() {
    currentSuggestion = if (originalText.isBlank()) {
      "Tap a tone to rewrite your message."
    } else {
      "Tap a tone to rewrite your message."
    }
  }
  
  /**
   * Set callback for streaming text updates.
   */
  fun setStreamingCallback(callback: (String) -> Unit) {
    streamingCallback = callback
  }

  /**
   * Set callback for when streaming completes.
   * Called with the final text after streaming is done.
   */
  fun setCompletionCallback(callback: (String) -> Unit) {
    completionCallback = callback
  }

  /**
   * Apply tone transformation using streaming LLM chat.
   */
  fun applyTone(toneType: ToneType, currentInputText: String = originalText) {
    // Move current to previous (but skip the initial placeholder)
    val placeholder = "Tap a tone to rewrite your message."
    if (currentSuggestion.isNotBlank() && currentSuggestion != placeholder) {
      previousSummary = currentSuggestion
    }

    // Reset current suggestion for streaming
    currentSuggestion = ""
    isStreaming = true

    Log.d(TAG, "Applying tone: $toneType to text: '$currentInputText'")

    // Use streaming chat for all tones
    scope.launch {
      try {
        textRewriteUseCase.execute(
          text = currentInputText,
          toneType = toneType,
          threadId = threadId
        )
          .onStart {
            Log.d(TAG, "Streaming started for tone: $toneType")
            currentSuggestion = "" // Clear for accumulation
          }
          .onEach { token ->
            // Accumulate tokens
            currentSuggestion += token
            // Notify callback for real-time UI update
            streamingCallback?.invoke(currentSuggestion)
          }
          .onCompletion { error ->
            isStreaming = false
            if (error == null) {
              Log.d(TAG, "Streaming completed. Final text: '$currentSuggestion'")
              // Notify completion callback with final text
              completionCallback?.invoke(currentSuggestion)
            }
          }
          .catch { e ->
            Log.e(TAG, "Error during tone rewrite streaming", e)
            currentSuggestion = "Error: ${e.message}"
            streamingCallback?.invoke(currentSuggestion)
          }
          .collect { } // Terminal operator - collection handled by onEach
      } catch (e: Exception) {
        Log.e(TAG, "Failed to apply tone: $toneType", e)
        isStreaming = false
        currentSuggestion = "Error: ${e.message}"
        streamingCallback?.invoke(currentSuggestion)
      }
    }
  }

  /**
   * Chat with Charles (conversational interaction).
   * This is different from applyTone - it's for actual dialogue, not tone transformations.
   *
   * @param userMessage The user's message/request
   * @param conversationHistory Optional history of the Charles conversation
   * @param currentDraft Optional current draft being worked on
   */
  fun chat(userMessage: String, conversationHistory: String? = null, currentDraft: String? = null) {
    // Reset current suggestion for streaming
    currentSuggestion = ""
    isStreaming = true

    Log.d(TAG, "Starting chat with Charles: '$userMessage'")

    scope.launch {
      try {
        chatUseCase.execute(
          userMessage = userMessage,
          conversationHistory = conversationHistory,
          currentDraft = currentDraft
        )
          .onStart {
            Log.d(TAG, "Chat streaming started")
            currentSuggestion = "" // Clear for accumulation
          }
          .onEach { token ->
            // Accumulate tokens
            currentSuggestion += token
            // Notify callback for real-time UI update
            streamingCallback?.invoke(currentSuggestion)
          }
          .onCompletion { error ->
            isStreaming = false
            if (error == null) {
              Log.d(TAG, "Chat completed. Final text: '$currentSuggestion'")
              // Notify completion callback with final text
              completionCallback?.invoke(currentSuggestion)
            }
          }
          .catch { e ->
            Log.e(TAG, "Error during chat streaming", e)
            currentSuggestion = "Error: ${e.message}"
            streamingCallback?.invoke(currentSuggestion)
          }
          .collect { } // Terminal operator - collection handled by onEach
      } catch (e: Exception) {
        Log.e(TAG, "Failed to chat", e)
        isStreaming = false
        currentSuggestion = "Error: ${e.message}"
        streamingCallback?.invoke(currentSuggestion)
      }
    }
  }

}

/**
 * Tone types from the enhanced spec.
 * These are transformation tools used within the chat context.
 */
enum class ToneType {
  HISTORY_JSON, FORMAL, FRIENDLY, CLARITY, SHORTEN, EXPAND
}
