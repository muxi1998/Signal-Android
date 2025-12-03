package com.mtkresearch.breeze.ui

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.signal.core.util.logging.Log
import com.mtkresearch.breeze.AISession
import com.mtkresearch.breeze.R
import com.mtkresearch.breeze.ToneType
import com.mtkresearch.breeze.WindowPreferences
import com.mtkresearch.breeze.rainbow.AnimationStateType
import com.mtkresearch.breeze.rainbow.RainbowGradientDrawable
import java.io.File
import java.io.FileOutputStream

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
    private val onChatMessage: (String) -> Unit,
    private val onStopRequest: () -> Unit = {},
    private val onTtsRequest: (String) -> Unit = {},
    private val onTtsStop: () -> Unit = {},
    private val onVoiceRecordingComplete: (File) -> Unit = {}
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
            onChatMessage: (String) -> Unit = {},
            onStopRequest: () -> Unit = {},
            onTtsRequest: (String) -> Unit = {},
            onTtsStop: () -> Unit = {},
            onVoiceRecordingComplete: (File) -> Unit = {}
        ): BreezeFloatingWindow {
            return BreezeFloatingWindow(
                context, anchorBounds, savedSettings, session,
                onAccept, onDismiss, onResize, onMove, onToneChange, onChatMessage, onStopRequest,
                onTtsRequest, onTtsStop, onVoiceRecordingComplete
            )
        }
    }

    // Response types for conversation messages with emojis
    enum class ResponseType(val emoji: String, val label: String) {
        USER("👤", "You"),              // User message (text input)
        USER_VOICE("👤🎤", "You"),      // User message (voice input)
        LLM("🤖", "Charles"),           // LLM response
        ASR("🎤", "Charles"),           // Speech-to-text result (deprecated, use USER_VOICE for user)
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
    private var draftContainer: FrameLayout? = null
    private var stopButton: TextView? = null
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

    // Rainbow animation for streaming
    private var rainbowDrawable: RainbowGradientDrawable? = null
    private var rainbowAnimator: ValueAnimator? = null

    // TTS state and UI
    enum class TtsState { IDLE, LOADING, PLAYING }
    private var ttsState: TtsState = TtsState.IDLE
    private var ttsButton: TextView? = null
    private var conversationWrapper: FrameLayout? = null
    private var ttsRainbowDrawable: RainbowGradientDrawable? = null
    private var ttsRainbowAnimator: ValueAnimator? = null

    // Voice input state and UI
    enum class InputState { NORMAL, RECORDING }
    private var inputState: InputState = InputState.NORMAL
    private var sendButton: ImageButton? = null
    private var inputNormalState: View? = null
    private var inputRecordingState: View? = null
    private var recordingPulse: View? = null
    private var recordingPulseAnimator: AnimatorSet? = null

    // Recording
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var audioFile: File? = null
    private var isRecording = false

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
        setupTtsButton()

        layoutParams = createLayoutParams()

        try {
            windowManager.addView(windowView, layoutParams)
            Log.d(TAG, "Floating window shown")
            // Draft is set explicitly by caller after show() - no automatic update needed
        } catch (e: Exception) {
            Log.e(TAG, "Error showing floating window", e)
        }
    }

    private fun setupViews() {
        windowView?.let { view ->
            draftField = view.findViewById(R.id.breeze_draft_field)
            draftContainer = view.findViewById(R.id.breeze_draft_container)
            stopButton = view.findViewById(R.id.breeze_stop_button)
            chatInput = view.findViewById(R.id.breeze_chat_input)
            conversationContainer = view.findViewById(R.id.breeze_conversation_container)
            conversationScroll = view.findViewById(R.id.breeze_conversation_scroll)
            ttsButton = view.findViewById(R.id.breeze_tts_button)
            conversationWrapper = view.findViewById(R.id.breeze_conversation_wrapper)

            // Voice input views
            sendButton = view.findViewById(R.id.breeze_chat_send)
            inputNormalState = view.findViewById(R.id.breeze_input_normal_state)
            inputRecordingState = view.findViewById(R.id.breeze_input_recording_state)
            recordingPulse = view.findViewById(R.id.breeze_chat_recording_pulse)

            // Setup stop button click handler
            stopButton?.setOnClickListener {
                Log.d(TAG, "Stop button clicked - cancelling request")
                onStopRequest()
                stopStreaming()
            }
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
        // Text watcher to toggle mic/send icon
        chatInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSendButtonIcon(s?.toString()?.isNotEmpty() == true)
            }
        })

        // Initial state: show mic icon (no text)
        updateSendButtonIcon(false)

        // Send/Mic button click
        sendButton?.setOnClickListener {
            val text = chatInput?.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                // Has text: send message
                sendChatMessage()
            } else {
                // No text: start voice recording
                startVoiceRecording()
            }
        }

        // Recording state: tap to stop
        inputRecordingState?.setOnClickListener {
            if (isRecording) {
                stopVoiceRecording()
            }
        }

        // IME action (keyboard send)
        chatInput?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val text = chatInput?.text?.toString()?.trim() ?: ""
                if (text.isNotEmpty()) {
                    sendChatMessage()
                }
                true
            } else false
        }
    }

    /**
     * Update send button icon and background based on whether there's text in the input.
     * Shows mic icon (light background) when empty, send icon (orange background) when has text.
     */
    private fun updateSendButtonIcon(hasText: Boolean) {
        sendButton?.apply {
            if (hasText) {
                setImageResource(R.drawable.ic_send_24)
                setBackgroundResource(R.drawable.breeze_send_button_background)
            } else {
                setImageResource(R.drawable.breeze_rainbow_mic)
                setBackgroundResource(R.drawable.breeze_mic_button_background)
            }
        }
    }

    // ==================== Voice Recording ====================

    /**
     * Start voice recording if permission is granted.
     */
    private fun startVoiceRecording() {
        if (!checkRecordPermission()) {
            Log.w(TAG, "Record permission not granted")
            return
        }

        setInputState(InputState.RECORDING)
        startRecording()
    }

    /**
     * Stop voice recording and process the audio.
     */
    private fun stopVoiceRecording() {
        stopRecording()
        setInputState(InputState.NORMAL)
    }

    /**
     * Check if record audio permission is granted.
     */
    private fun checkRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Set the input state (normal or recording).
     */
    private fun setInputState(state: InputState) {
        inputState = state
        handler.post {
            when (state) {
                InputState.NORMAL -> {
                    inputNormalState?.visibility = View.VISIBLE
                    inputRecordingState?.visibility = View.GONE
                    stopRecordingPulseAnimation()
                }
                InputState.RECORDING -> {
                    inputNormalState?.visibility = View.GONE
                    inputRecordingState?.visibility = View.VISIBLE
                    startRecordingPulseAnimation()
                    // Hide keyboard when starting recording
                    hideKeyboard()
                }
            }
        }
    }

    /**
     * Start the actual audio recording.
     */
    private fun startRecording() {
        try {
            audioFile = File.createTempFile("breeze_chat_recording_", ".pcm", context.cacheDir)

            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Permission not granted for recording")
                return
            }

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingThread = Thread {
                writeAudioDataToFile()
            }
            recordingThread?.start()

            Log.d(TAG, "Recording started: ${audioFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            isRecording = false
            setInputState(InputState.NORMAL)
        }
    }

    /**
     * Write audio data to file in background thread.
     */
    private fun writeAudioDataToFile() {
        val data = ByteArray(1024)
        var os: FileOutputStream? = null
        try {
            os = FileOutputStream(audioFile)
            while (isRecording) {
                val read = audioRecord?.read(data, 0, data.size) ?: 0
                if (read > 0) {
                    os.write(data, 0, read)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing audio data", e)
        } finally {
            try {
                os?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing audio file", e)
            }
        }
    }

    /**
     * Stop recording and trigger the callback with the audio file.
     */
    private fun stopRecording() {
        if (!isRecording) return

        isRecording = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            recordingThread?.join(1000)
            recordingThread = null

            Log.d(TAG, "Recording stopped: ${audioFile?.absolutePath}")

            // Notify callback with audio file for ASR processing
            audioFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    onVoiceRecordingComplete(file)
                } else {
                    Log.w(TAG, "Recording file is empty or doesn't exist")
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
    }

    /**
     * Start pulse animation for recording indicator.
     */
    private fun startRecordingPulseAnimation() {
        stopRecordingPulseAnimation()

        recordingPulse?.let { pulse ->
            val scaleX = ObjectAnimator.ofFloat(pulse, "scaleX", 1f, 1.3f, 1f)
            val scaleY = ObjectAnimator.ofFloat(pulse, "scaleY", 1f, 1.3f, 1f)
            val alpha = ObjectAnimator.ofFloat(pulse, "alpha", 0.6f, 0.2f, 0.6f)

            recordingPulseAnimator = AnimatorSet().apply {
                playTogether(scaleX, scaleY, alpha)
                duration = 1000
                interpolator = AccelerateDecelerateInterpolator()
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (isRecording) {
                            start()
                        }
                    }
                })
                start()
            }
        }
    }

    /**
     * Stop pulse animation for recording indicator.
     */
    private fun stopRecordingPulseAnimation() {
        recordingPulseAnimator?.cancel()
        recordingPulseAnimator = null
    }

    /**
     * Hide keyboard.
     */
    private fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        chatInput?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun setupTtsButton() {
        ttsButton?.setOnClickListener {
            when (ttsState) {
                TtsState.IDLE -> {
                    // Find latest Charles message to speak
                    val latestCharlesMessage = conversationHistory
                        .lastOrNull { !it.isUser }
                        ?.text

                    if (latestCharlesMessage != null) {
                        Log.d(TAG, "TTS requested for: '${latestCharlesMessage.take(50)}...'")
                        setTtsState(TtsState.LOADING)
                        onTtsRequest(latestCharlesMessage)
                    } else {
                        Log.d(TAG, "No Charles message to speak")
                    }
                }
                TtsState.LOADING, TtsState.PLAYING -> {
                    Log.d(TAG, "TTS stop requested")
                    onTtsStop()
                    setTtsState(TtsState.IDLE)
                }
            }
        }
    }

    /**
     * Set TTS state and update button appearance.
     * Call this from BreezeManager when TTS state changes.
     */
    fun setTtsState(state: TtsState) {
        ttsState = state
        handler.post {
            when (state) {
                TtsState.IDLE -> {
                    ttsButton?.text = "🔊"
                    ttsButton?.setBackgroundResource(R.drawable.breeze_tts_button_background)
                    stopTtsAnimation()
                }
                TtsState.LOADING -> {
                    ttsButton?.text = "⏳"
                    ttsButton?.setBackgroundResource(R.drawable.breeze_tts_button_background)
                }
                TtsState.PLAYING -> {
                    ttsButton?.text = "⏹"
                    ttsButton?.setBackgroundResource(R.drawable.breeze_tts_button_background_active)
                    startTtsAnimation()
                }
            }
        }
    }

    /**
     * Start rainbow animation on conversation wrapper when TTS is playing.
     */
    private fun startTtsAnimation() {
        conversationWrapper?.let { wrapper ->
            Log.d(TAG, "Starting TTS rainbow animation")

            if (ttsRainbowDrawable == null) {
                ttsRainbowDrawable = RainbowGradientDrawable(context)
            }

            wrapper.foreground = ttsRainbowDrawable

            ttsRainbowAnimator?.cancel()
            ttsRainbowAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 2000 // Slower cycle for TTS playback
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    val rotation = animation.animatedValue as Float
                    ttsRainbowDrawable?.updateAnimation(rotation, AnimationStateType.TYPING)
                }
                start()
            }
        }
    }

    /**
     * Stop TTS rainbow animation.
     */
    private fun stopTtsAnimation() {
        Log.d(TAG, "Stopping TTS rainbow animation")
        ttsRainbowAnimator?.cancel()
        ttsRainbowAnimator = null
        conversationWrapper?.foreground = null
        ttsRainbowDrawable = null
    }

    /**
     * Show the TTS button (call when Charles has responded).
     */
    private fun showTtsButton() {
        handler.post {
            ttsButton?.visibility = View.VISIBLE
        }
    }

    /**
     * Hide the TTS button.
     */
    private fun hideTtsButton() {
        handler.post {
            ttsButton?.visibility = View.GONE
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
                // Format: For user messages, just emoji (human emoji is clear enough)
                // For Charles messages, emoji + label
                val prefix = if (isUser) {
                    "${responseType.emoji} "
                } else {
                    "${responseType.emoji} ${responseType.label}: "
                }
                this.text = "$prefix$text"
                textSize = 13f
                setTextColor(if (isUser) 0xFF333333.toInt() else 0xFF6B4EFF.toInt())
                setPadding(0, 8, 0, 8)
            }
            container.addView(messageView)

            // Scroll to bottom smoothly
            conversationScroll?.post {
                conversationScroll?.smoothScrollTo(0, container.height)
            }
        }
    }

    /**
     * Add a Charles response to conversation with specific response type.
     * Convenience method for adding AI responses.
     * Also shows the TTS button so user can listen to Charles's response.
     */
    fun addCharlesResponse(text: String, responseType: ResponseType) {
        addToConversation(isUser = false, text = text, responseType = responseType)
        // Show TTS button when Charles has something to say
        showTtsButton()
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
     * Shows rainbow animation and auto-scrolls to show latest content.
     */
    fun updateStreamingText(partialText: String) {
        handler.post {
            // Start rainbow animation if not already running
            if (rainbowAnimator?.isRunning != true) {
                startRainbowAnimation()
            }

            draftField?.setText(partialText)

            // Auto-scroll to bottom to show latest content
            draftField?.let { editText ->
                val scrollAmount = editText.layout?.let { layout ->
                    layout.getLineTop(editText.lineCount) - editText.height
                } ?: 0
                if (scrollAmount > 0) {
                    editText.scrollTo(0, scrollAmount)
                }
            }
        }
    }

    /**
     * Start rainbow border animation on the draft container (not EditText).
     * Using the container prevents the rainbow from scrolling with EditText content.
     */
    private fun startRainbowAnimation() {
        draftContainer?.let { container ->
            Log.d(TAG, "Starting rainbow animation on draft container")

            // Show stop button
            stopButton?.visibility = View.VISIBLE

            // Create rainbow drawable if not exists
            if (rainbowDrawable == null) {
                rainbowDrawable = RainbowGradientDrawable(context)
            }

            // Apply as foreground on container (not EditText) to prevent scroll issues
            container.foreground = rainbowDrawable

            // Create and start animator
            rainbowAnimator?.cancel()
            rainbowAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 1500 // Fast cycle for streaming
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    val rotation = animation.animatedValue as Float
                    rainbowDrawable?.updateAnimation(rotation, AnimationStateType.TYPING)
                }
                start()
            }
        }
    }

    /**
     * Stop rainbow border animation on the draft container.
     */
    private fun stopRainbowAnimation() {
        Log.d(TAG, "Stopping rainbow animation on draft container")
        rainbowAnimator?.cancel()
        rainbowAnimator = null
        draftContainer?.foreground = null
        rainbowDrawable = null
        // Hide stop button
        stopButton?.visibility = View.GONE
    }

    /**
     * Stop streaming and clean up animation state.
     * Called when user clicks stop button or streaming completes.
     */
    fun stopStreaming() {
        handler.post {
            stopRainbowAnimation()
        }
    }

    /**
     * Update session and refresh UI.
     * @param newSession The updated AI session
     * @param responseType The type of AI response (LLM, TONE, etc.)
     */
    fun updateSession(newSession: AISession, responseType: ResponseType = ResponseType.LLM) {
        session = newSession

        // Ensure UI updates run on main thread
        handler.post {
            // Stop rainbow animation since streaming is complete
            stopRainbowAnimation()

            updateDraft(session.currentSuggestion)

            // Add Charles's actual response to conversation (not a generic message)
            // This ensures TTS speaks the actual response, not a meta-message
            if (session.currentSuggestion.isNotBlank()) {
                addCharlesResponse(session.currentSuggestion, responseType)
            }
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
            // Stop rainbow animation before removing
            stopRainbowAnimation()
            // Stop TTS animation and playback
            stopTtsAnimation()
            if (ttsState != TtsState.IDLE) {
                onTtsStop()
                ttsState = TtsState.IDLE
            }
            // Stop recording if in progress
            if (isRecording) {
                isRecording = false
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
                audioFile?.delete()
                audioFile = null
            }
            stopRecordingPulseAnimation()

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
