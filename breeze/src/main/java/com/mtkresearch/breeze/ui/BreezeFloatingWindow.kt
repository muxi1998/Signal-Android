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

    // Conversation history
    data class ChatMessage(val isUser: Boolean, val text: String)
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

    fun show() {
        remove() // Ensure clean state

        val inflater = LayoutInflater.from(context)
        windowView = inflater.inflate(R.layout.breeze_floating_window, null)

        setupViews()
        setupToneChips()
        setupDragHandle()
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

    private fun setupToneChips() {
        windowView?.let { view ->
            view.findViewById<TextView>(R.id.breeze_tone_casual)?.setOnClickListener {
                onToneChange(ToneType.CASUAL)
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
            view.findViewById<TextView>(R.id.breeze_tone_concise)?.setOnClickListener {
                onToneChange(ToneType.CLARITY)
                highlightToneChip(it as TextView)
            }
            view.findViewById<TextView>(R.id.breeze_tone_professional)?.setOnClickListener {
                onToneChange(ToneType.PROFESSIONAL)
                highlightToneChip(it as TextView)
            }
        }
    }

    private fun highlightToneChip(selected: TextView) {
        // Reset all chips
        windowView?.let { view ->
            listOf(
                R.id.breeze_tone_casual,
                R.id.breeze_tone_formal,
                R.id.breeze_tone_friendly,
                R.id.breeze_tone_concise,
                R.id.breeze_tone_professional
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

    private fun setupDraftField() {
        windowView?.let { view ->
            // Submit button
            view.findViewById<ImageButton>(R.id.breeze_draft_submit)?.setOnClickListener {
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
     */
    fun addToConversation(isUser: Boolean, text: String) {
        conversationHistory.add(ChatMessage(isUser, text))

        conversationContainer?.let { container ->
            val messageView = TextView(context).apply {
                val prefix = if (isUser) "You: " else "Charles: "
                this.text = "$prefix$text"
                textSize = 13f
                setTextColor(if (isUser) 0xFF333333.toInt() else 0xFF6B4EFF.toInt())
                setPadding(0, 4, 0, 4)
            }
            container.addView(messageView)

            // Scroll to bottom
            conversationScroll?.post {
                conversationScroll?.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    /**
     * Update the draft field with new text.
     */
    fun updateDraft(text: String) {
        draftField?.setText(text)
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
     */
    fun updateSession(newSession: AISession) {
        session = newSession
        updateDraft(session.currentSuggestion)

        // Add Charles's response to conversation
        if (session.currentSuggestion.isNotBlank()) {
            addToConversation(isUser = false, "Here's an updated draft.")
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Use saved position or calculate default
        val width = (screenWidth * 0.9).toInt()
        val height = WindowManager.LayoutParams.WRAP_CONTENT

        val x = savedSettings.x.takeIf { it != 0 } ?: ((screenWidth - width) / 2)
        val y = savedSettings.y.takeIf { it != 0 } ?: (screenHeight / 3)

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
