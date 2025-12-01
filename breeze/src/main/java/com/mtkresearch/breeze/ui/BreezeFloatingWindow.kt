package com.mtkresearch.breeze.ui

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.signal.core.util.logging.Log
import com.mtkresearch.breeze.AISession
import com.mtkresearch.breeze.R
import com.mtkresearch.breeze.ToneType
import com.mtkresearch.breeze.WindowPreferences

/**
 * Floating conversation window for discussing with Charles (AI).
 *
 * Layout:
 * - Tone chips (quick tone changes)
 * - Conversation history (scrollable)
 * - Draft field (editable, with submit button)
 * - Chat input (to continue discussing)
 */
class BreezeFloatingWindow private constructor(
    private val context: Context,
    private val anchorBounds: Rect,
    private val savedSettings: WindowPreferences.WindowSettings,
    private var session: AISession,
    private val onAccept: (String) -> Unit,
    private val onDismiss: () -> Unit,
    private val onResize: (Int, Int, Int, Int) -> Unit,
    private val onMove: (Int, Int, Int, Int) -> Unit,
    private val onToneChange: (ToneType) -> Unit,
    private val onChatMessage: (String) -> Unit
) {

    companion object {
        private val TAG = Log.tag(BreezeFloatingWindow::class.java)

        fun create(
            context: Context,
            anchorBounds: Rect,
            savedSettings: WindowPreferences.WindowSettings,
            session: AISession,
            onAccept: (String) -> Unit,
            onDismiss: () -> Unit,
            onResize: (Int, Int, Int, Int) -> Unit,
            onMove: (Int, Int, Int, Int) -> Unit,
            onToneChange: (ToneType) -> Unit,
            onChatMessage: (String) -> Unit = {}
        ): BreezeFloatingWindow {
            return BreezeFloatingWindow(
                context, anchorBounds, savedSettings, session,
                onAccept, onDismiss, onResize, onMove, onToneChange, onChatMessage
            )
        }
    }

    // Response types for conversation messages with emojis
    enum class ResponseType(val emoji: String, val label: String) {
        USER("👤", "You"),              // User message
        LLM("🤖", "Charles"),           // LLM response
        ASR("🎤", "Charles"),           // Speech-to-text result
        TTS("🔊", "Charles"),           // Text-to-speech result
        TONE("✨", "Charles"),          // Tone transformation result
        HISTORY("📋", "Charles"),       // History in JSON result
        SYSTEM("ℹ️", "Charles")         // System message
    }

    // Conversation history
    data class ChatMessage(val isUser: Boolean, val text: String, val responseType: ResponseType = ResponseType.USER)
    private val conversationHistory = mutableListOf<ChatMessage>()

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var windowView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())

    // UI Components
    private var draftField: EditText? = null
    private var chatInput: EditText? = null
    private var conversationContainer: LinearLayout? = null
    private var conversationScroll: ScrollView? = null

    // Drag handling
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    // Resize handling
    private var initialWidth = 0
    private var initialHeight = 0
    private var minWidth = 300
    private var minHeight = 400

    fun show() {
        remove() // Ensure clean state

        val inflater = LayoutInflater.from(context)
        windowView = inflater.inflate(R.layout.breeze_floating_window, null)

        setupViews()
        setupCloseButton()
        setupToneChips()
        setupDragHandle()
        setupResizeHandle()
        setupDraftField()
        setupChatInput()

        layoutParams = createLayoutParams()

        try {
            windowManager.addView(windowView, layoutParams)
            Log.d(TAG, "Floating window shown")

            // Update content after a short delay to ensure view is laid out
            handler.postDelayed({
                updateDraft(session.currentSuggestion)
            }, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing floating window", e)
        }
    }

    private fun setupViews() {
        windowView?.let { view ->
            draftField = view.findViewById(R.id.breeze_draft_field)
            chatInput = view.findViewById(R.id.breeze_chat_input)
            conversationContainer = view.findViewById(R.id.breeze_conversation_container)
            conversationScroll = view.findViewById(R.id.breeze_conversation_scroll)
        }
    }

    private fun setupCloseButton() {
        windowView?.findViewById<ImageButton>(R.id.breeze_close_button)?.setOnClickListener {
            Log.d(TAG, "Close button clicked - dismissing window")
            onDismiss()
            remove()
        }
    }

    private fun setupToneChips() {
        windowView?.let { view ->
            // ToneType enum values: HISTORY_JSON, FORMAL, FRIENDLY, CLARITY, SHORTEN, EXPAND
            view.findViewById<TextView>(R.id.breeze_tone_history)?.setOnClickListener {
                onToneChange(ToneType.HISTORY_JSON)
                highlightToneChip(it as TextView)
            }
            view.findViewById<TextView>(R.id.breeze_tone_formal)?.setOnClickListener {
                onToneChange(ToneType.FORMAL)
                highlightToneChip(it as TextView)
            }
            view.findViewById<TextView>(R.id.breeze_tone_friendly)?.setOnClickListener {
                onToneChange(ToneType.FRIENDLY)
                highlightToneChip(it as TextView)
            }
            view.findViewById<TextView>(R.id.breeze_tone_clarity)?.setOnClickListener {
                onToneChange(ToneType.CLARITY)
                highlightToneChip(it as TextView)
            }
            view.findViewById<TextView>(R.id.breeze_tone_shorten)?.setOnClickListener {
                onToneChange(ToneType.SHORTEN)
                highlightToneChip(it as TextView)
            }
            view.findViewById<TextView>(R.id.breeze_tone_expand)?.setOnClickListener {
                onToneChange(ToneType.EXPAND)
                highlightToneChip(it as TextView)
            }
        }
    }

    private fun highlightToneChip(selected: TextView) {
        // Reset all chips
        windowView?.let { view ->
            listOf(
                R.id.breeze_tone_history,
                R.id.breeze_tone_formal,
                R.id.breeze_tone_friendly,
                R.id.breeze_tone_clarity,
                R.id.breeze_tone_shorten,
                R.id.breeze_tone_expand
            ).forEach { id ->
                view.findViewById<TextView>(id)?.setBackgroundResource(R.drawable.breeze_tone_chip_background)
            }
        }
        // Highlight selected
        selected.setBackgroundResource(R.drawable.breeze_tone_chip_background_selected)
    }

    private fun setupDragHandle() {
        windowView?.findViewById<View>(R.id.breeze_drag_handle)?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams?.x ?: 0
                    initialY = layoutParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams?.let { params ->
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(windowView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    layoutParams?.let { params ->
                        onMove(params.x, params.y, params.width, params.height)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupResizeHandle() {
        // Setup all 4 corner resize handles
        setupCornerResize(R.id.breeze_resize_top_left, ResizeCorner.TOP_LEFT)
        setupCornerResize(R.id.breeze_resize_top_right, ResizeCorner.TOP_RIGHT)
        setupCornerResize(R.id.breeze_resize_bottom_left, ResizeCorner.BOTTOM_LEFT)
        setupCornerResize(R.id.breeze_resize_bottom_right, ResizeCorner.BOTTOM_RIGHT)
    }

    private enum class ResizeCorner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    private fun setupCornerResize(viewId: Int, corner: ResizeCorner) {
        windowView?.findViewById<View>(viewId)?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams?.x ?: 0
                    initialY = layoutParams?.y ?: 0
                    initialWidth = layoutParams?.width ?: 0
                    initialHeight = layoutParams?.height ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams?.let { params ->
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()

                        when (corner) {
                            ResizeCorner.TOP_LEFT -> {
                                val newWidth = initialWidth - deltaX
                                val newHeight = initialHeight - deltaY
                                if (newWidth >= minWidth) {
                                    params.width = newWidth
                                    params.x = initialX + deltaX
                                }
                                if (newHeight >= minHeight) {
                                    params.height = newHeight
                                    params.y = initialY + deltaY
                                }
                            }
                            ResizeCorner.TOP_RIGHT -> {
                                val newWidth = initialWidth + deltaX
                                val newHeight = initialHeight - deltaY
                                params.width = maxOf(minWidth, newWidth)
                                if (newHeight >= minHeight) {
                                    params.height = newHeight
                                    params.y = initialY + deltaY
                                }
                            }
                            ResizeCorner.BOTTOM_LEFT -> {
                                val newWidth = initialWidth - deltaX
                                val newHeight = initialHeight + deltaY
                                if (newWidth >= minWidth) {
                                    params.width = newWidth
                                    params.x = initialX + deltaX
                                }
                                params.height = maxOf(minHeight, newHeight)
                            }
                            ResizeCorner.BOTTOM_RIGHT -> {
                                params.width = maxOf(minWidth, initialWidth + deltaX)
                                params.height = maxOf(minHeight, initialHeight + deltaY)
                            }
                        }
                        windowManager.updateViewLayout(windowView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    layoutParams?.let { params ->
                        onResize(params.x, params.y, params.width, params.height)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupDraftField() {
        windowView?.let { view ->
            // Accept button
            view.findViewById<TextView>(R.id.breeze_draft_submit)?.setOnClickListener {
                val draft = draftField?.text?.toString() ?: ""
                if (draft.isNotBlank()) {
                    Log.d(TAG, "Draft submitted: $draft")
                    onAccept(draft)
                    remove()
                }
            }
        }
    }

    private fun setupChatInput() {
        windowView?.let { view ->
            val sendButton = view.findViewById<ImageButton>(R.id.breeze_chat_send)

            // Send button click
            sendButton?.setOnClickListener {
                sendChatMessage()
            }

            // IME action (keyboard send)
            chatInput?.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendChatMessage()
                    true
                } else false
            }
        }
    }

    private fun sendChatMessage() {
        val message = chatInput?.text?.toString()?.trim() ?: ""
        if (message.isNotBlank()) {
            Log.d(TAG, "Chat message sent: $message")

            // Add to conversation history
            addToConversation(isUser = true, message)

            // Clear input
            chatInput?.setText("")

            // Hide keyboard
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            chatInput?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }

            // Notify callback
            onChatMessage(message)
        }
    }

    /**
     * Add a message to the conversation history.
     * @param isUser True if this is a user message
     * @param text The message text
     * @param responseType The type of response (for Charles messages, determines emoji)
     */
    fun addToConversation(isUser: Boolean, text: String, responseType: ResponseType = if (isUser) ResponseType.USER else ResponseType.LLM) {
        conversationHistory.add(ChatMessage(isUser, text, responseType))

        conversationContainer?.let { container ->
            val messageView = TextView(context).apply {
                // Format: emoji + label + text
                val prefix = "${responseType.emoji} ${responseType.label}: "
                this.text = "$prefix$text"
                textSize = 13f
                setTextColor(if (isUser) 0xFF333333.toInt() else 0xFF6B4EFF.toInt())
                setPadding(0, 8, 0, 8)
            }
            container.addView(messageView)

            // Scroll to bottom
            conversationScroll?.post {
                conversationScroll?.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    /**
     * Add a Charles response to conversation with specific response type.
     * Convenience method for adding AI responses.
     */
    fun addCharlesResponse(text: String, responseType: ResponseType) {
        addToConversation(isUser = false, text = text, responseType = responseType)
    }

    /**
     * Update the draft field with new text.
     */
    fun updateDraft(text: String) {
        draftField?.setText(text)
    }

    /**
     * Get current draft text from the draft field.
     */
    fun getCurrentDraft(): String {
        return draftField?.text?.toString() ?: ""
    }

    /**
     * Get conversation history as a formatted string for LLM context.
     */
    fun getConversationHistoryForLLM(): String {
        if (conversationHistory.isEmpty()) return ""
        return conversationHistory.joinToString("\n") { msg ->
            val role = if (msg.isUser) "User" else "Charles"
            "$role: ${msg.text}"
        }
    }

    /**
     * Update draft with streaming text.
     */
    fun updateStreamingText(partialText: String) {
        handler.post {
            draftField?.setText(partialText)
        }
    }

    /**
     * Update session and refresh UI.
     * @param newSession The updated AI session
     * @param responseType The type of AI response (LLM, TONE, etc.)
     */
    fun updateSession(newSession: AISession, responseType: ResponseType = ResponseType.LLM) {
        session = newSession
        updateDraft(session.currentSuggestion)

        // Add Charles's response to conversation with appropriate emoji
        if (session.currentSuggestion.isNotBlank()) {
            val message = when (responseType) {
                ResponseType.TONE -> "I've applied the tone transformation."
                ResponseType.HISTORY -> "Here's your message with conversation history."
                ResponseType.ASR -> "I've transcribed your voice input."
                else -> "Here's an updated draft."
            }
            addCharlesResponse(message, responseType)
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Use saved settings or calculate default
        val width = savedSettings.width.takeIf { it > 0 } ?: (screenWidth * 0.9).toInt()
        val height = savedSettings.height.takeIf { it > 0 } ?: (screenHeight * 0.5).toInt()

        val x = savedSettings.x.takeIf { it != 0 } ?: ((screenWidth - width) / 2)
        val y = savedSettings.y.takeIf { it != 0 } ?: (screenHeight / 4)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    fun remove() {
        try {
            windowView?.let {
                windowManager.removeView(it)
                Log.d(TAG, "Floating window removed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing floating window", e)
        }
        windowView = null
        layoutParams = null
        conversationHistory.clear()
    }

    fun isShowing(): Boolean = windowView != null
}
