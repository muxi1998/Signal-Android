package com.mtkresearch.breeze.ui

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mtkresearch.breeze.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Manages the Breeze input choice popup with multiple states:
 * 1. Choice: Voice vs Text selection
 * 2. Text Input: EditText with magic send button
 * 3. Recording: Voice recording with animation
 * 4. Processing: "Charles is working..." progress indicator
 */
class BreezeInputChoicePopup(
    private val activity: Activity,
    private val anchorBounds: Rect,
    private val onTextSubmit: (String) -> Unit,
    private val onRecordingComplete: (File) -> Unit,
    private val onDismiss: () -> Unit
) {
    enum class State {
        CHOICE,
        TEXT_INPUT,
        RECORDING,
        PROCESSING
    }

    private var popupWindow: PopupWindow? = null
    private var popupView: View? = null
    private var currentState = State.CHOICE

    // Views for different states
    private var stateChoice: View? = null
    private var stateTextInput: View? = null
    private var stateRecording: View? = null
    private var stateProcessing: View? = null

    // Recording views
    private var recordingPulse: View? = null
    private var recordingIndicator: View? = null
    private var pulseAnimator: AnimatorSet? = null

    // Processing views
    private var processingCheck: ImageView? = null
    private var processingText: TextView? = null
    private var processingCancel: TextView? = null
    private var processingScroll: ScrollView? = null

    // Text input views
    private var textInput: EditText? = null
    private var sendButton: ImageButton? = null

    // Recording
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var audioFile: File? = null
    private var isRecording = false

    // Permission handling
    private var pendingRecordingStart = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    // Colors for send button
    private val colorDisabled = android.graphics.Color.WHITE
    private val colorEnabled = android.graphics.Color.parseColor("#FF6B4E") // Orange

    // For tracking anchor view to follow position changes
    private var anchorView: View? = null
    private var rootView: View? = null
    private var layoutListener: android.view.ViewTreeObserver.OnGlobalLayoutListener? = null

    fun show() {
        rootView = activity.window.decorView.findViewById<View>(android.R.id.content)
        if (rootView == null) {
            Log.e(TAG, "Cannot show popup - no root view")
            return
        }

        // Hide keyboard naturally (no forced timing)
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        activity.currentFocus?.let { focus ->
            imm.hideSoftInputFromWindow(focus.windowToken, 0)
        }

        showPopupAtPosition(rootView!!)

        // Listen for layout changes to reposition popup (e.g., when keyboard hides)
        setupLayoutListener()
    }

    private fun setupLayoutListener() {
        layoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            if (popupWindow?.isShowing == true && currentState == State.CHOICE) {
                // Recalculate position based on fresh anchor bounds
                updatePopupPosition()
            }
        }
        rootView?.viewTreeObserver?.addOnGlobalLayoutListener(layoutListener)
    }

    private fun removeLayoutListener() {
        layoutListener?.let { listener ->
            rootView?.viewTreeObserver?.removeOnGlobalLayoutListener(listener)
        }
        layoutListener = null
    }

    private fun updatePopupPosition() {
        val popup = popupWindow ?: return
        val view = popupView ?: return

        val screenWidth = activity.resources.displayMetrics.widthPixels
        val density = activity.resources.displayMetrics.density
        val padding = (16 * density).toInt()

        val popupWidth = view.measuredWidth.takeIf { it > 0 } ?: (200 * density).toInt()
        val popupHeight = view.measuredHeight.takeIf { it > 0 } ?: (100 * density).toInt()

        // Center horizontally
        var x = anchorBounds.centerX() - (popupWidth / 2)
        x = x.coerceIn(padding, screenWidth - popupWidth - padding)

        // Position above anchor - but we need fresh anchor position!
        // Since we can't get fresh bounds here, use a fixed position from bottom
        val screenHeight = activity.resources.displayMetrics.heightPixels
        val bottomMargin = (80 * density).toInt() // Fixed margin from bottom
        val y = screenHeight - bottomMargin - popupHeight

        popup.update(x, y, -1, -1)
        Log.d(TAG, "Updated popup position: ($x, $y)")
    }

    private fun showPopupAtPosition(rootView: View) {
        val inflater = LayoutInflater.from(activity)
        popupView = inflater.inflate(R.layout.breeze_input_choice_popup, null)

        setupViews()
        setupClickListeners()
        setupTextWatcher()

        // Measure the popup to get its dimensions
        popupView?.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = popupView?.measuredWidth ?: 200
        val popupHeight = popupView?.measuredHeight ?: 100

        popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            elevation = 10f
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            setOnDismissListener {
                cleanup()
                onDismiss()
            }
        }

        // Get screen dimensions
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val density = activity.resources.displayMetrics.density
        val padding = (16 * density).toInt()

        // Center the popup horizontally, but keep within screen bounds
        var x = anchorBounds.centerX() - (popupWidth / 2)
        x = x.coerceIn(padding, screenWidth - popupWidth - padding)

        // Position popup just above the anchor (mic button location)
        // The anchor bounds are updated at click time, so they reflect current position
        val y = anchorBounds.top - popupHeight - (8 * density).toInt()

        Log.d(TAG, "Showing popup: anchor=$anchorBounds, popupSize=${popupWidth}x${popupHeight}, position=($x, $y)")
        popupWindow?.showAtLocation(rootView, Gravity.NO_GRAVITY, x, y)

        setState(State.CHOICE)
    }

    private fun setupTextWatcher() {
        textInput?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateSendButtonColor(s?.toString()?.isNotEmpty() == true)
            }
        })
        // Initial state
        updateSendButtonColor(false)
    }

    private fun updateSendButtonColor(hasText: Boolean) {
        sendButton?.let { button ->
            // Change background: orange when has text, gray when empty
            val backgroundRes = if (hasText) {
                R.drawable.breeze_send_button_background
            } else {
                R.drawable.breeze_send_button_disabled
            }
            button.setBackgroundResource(backgroundRes)
        }
    }

    private fun setupViews() {
        popupView?.let { view ->
            stateChoice = view.findViewById(R.id.breeze_state_choice)
            stateTextInput = view.findViewById(R.id.breeze_state_text_input)
            stateRecording = view.findViewById(R.id.breeze_state_recording)
            stateProcessing = view.findViewById(R.id.breeze_state_processing)

            // Recording views
            recordingPulse = view.findViewById(R.id.breeze_recording_pulse)
            recordingIndicator = view.findViewById(R.id.breeze_recording_indicator)

            // Processing views
            processingCheck = view.findViewById(R.id.breeze_processing_check)
            processingText = view.findViewById(R.id.breeze_processing_text)
            processingCancel = view.findViewById(R.id.breeze_processing_cancel)
            processingScroll = view.findViewById(R.id.breeze_processing_scroll)

            // Text input views
            textInput = view.findViewById(R.id.breeze_text_input)
            sendButton = view.findViewById(R.id.breeze_text_send)
        }
    }

    private fun setupClickListeners() {
        popupView?.let { view ->
            // Choice: Voice button
            view.findViewById<View>(R.id.breeze_choice_voice)?.setOnClickListener {
                Log.d(TAG, "Voice selected")
                startRecordingWithPermissionCheck()
            }

            // Choice: Text button
            view.findViewById<View>(R.id.breeze_choice_text)?.setOnClickListener {
                Log.d(TAG, "Text selected")
                setState(State.TEXT_INPUT)
                showKeyboard()
            }

            // Recording button (tap to stop) - set on the whole recording state container
            view.findViewById<View>(R.id.breeze_recording_button)?.setOnClickListener {
                Log.d(TAG, "Recording button tapped, isRecording=$isRecording")
                if (isRecording) {
                    stopRecording()
                }
            }

            // Also set click listener on the entire recording state for easier tapping
            stateRecording?.setOnClickListener {
                Log.d(TAG, "Recording state tapped, isRecording=$isRecording")
                if (isRecording) {
                    stopRecording()
                }
            }

            // Send button
            sendButton?.setOnClickListener {
                val text = textInput?.text?.toString()?.trim() ?: ""
                if (text.isNotEmpty()) {
                    Log.d(TAG, "Sending text: $text")
                    hideKeyboard()
                    setState(State.PROCESSING)
                    onTextSubmit(text)
                }
            }

            // Processing cancel button
            view.findViewById<View>(R.id.breeze_processing_cancel)?.setOnClickListener {
                Log.d(TAG, "Cancel button clicked - dismissing popup")
                dismiss()
            }
        }
    }

    private fun startRecordingWithPermissionCheck() {
        if (checkRecordPermission()) {
            setState(State.RECORDING)
            startRecording()
        } else {
            // Mark that we want to start recording after permission is granted
            pendingRecordingStart = true
            requestRecordPermission()
        }
    }

    private fun checkRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRecordPermission() {
        Log.d(TAG, "Requesting RECORD_AUDIO permission")
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_CODE
        )
        Toast.makeText(activity, "Please grant microphone permission to record", Toast.LENGTH_SHORT).show()

        // Start polling for permission grant
        startPermissionPolling()
    }

    private fun startPermissionPolling() {
        // Poll for permission grant since we can't rely on callback in popup context
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (pendingRecordingStart) {
                    if (checkRecordPermission()) {
                        Log.d(TAG, "Permission granted, auto-starting recording")
                        pendingRecordingStart = false
                        setState(State.RECORDING)
                        startRecording()
                    } else if (isShowing()) {
                        // Keep polling if popup is still showing
                        handler.postDelayed(this, 500)
                    } else {
                        pendingRecordingStart = false
                    }
                }
            }
        }, 500)
    }

    /**
     * Call this when permission result is received.
     * Can be called from the hosting Activity/Fragment.
     */
    fun onPermissionResult(granted: Boolean) {
        if (granted && pendingRecordingStart) {
            Log.d(TAG, "Permission granted via callback, starting recording")
            pendingRecordingStart = false
            setState(State.RECORDING)
            startRecording()
        } else if (!granted) {
            pendingRecordingStart = false
            Toast.makeText(activity, "Microphone permission is required for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private val TAG = Log.tag(BreezeInputChoicePopup::class.java)
        const val PERMISSION_REQUEST_CODE = 1001
    }

    fun setState(state: State) {
        currentState = state
        Log.d(TAG, "Setting state to: $state")

        stateChoice?.visibility = if (state == State.CHOICE) View.VISIBLE else View.GONE
        stateTextInput?.visibility = if (state == State.TEXT_INPUT) View.VISIBLE else View.GONE
        stateRecording?.visibility = if (state == State.RECORDING) View.VISIBLE else View.GONE
        stateProcessing?.visibility = if (state == State.PROCESSING) View.VISIBLE else View.GONE

        when (state) {
            State.RECORDING -> startPulseAnimation()
            State.PROCESSING -> {
                stopPulseAnimation()
                // Reset processing state: show cancel button, hide check icon
                processingCancel?.visibility = View.VISIBLE
                processingCheck?.visibility = View.GONE
                processingText?.text = "Charles is working..."
            }
            else -> stopPulseAnimation()
        }

        // Update popup size
        popupWindow?.update()
    }

    private fun startPulseAnimation() {
        stopPulseAnimation()

        recordingPulse?.let { pulse ->
            val scaleX = ObjectAnimator.ofFloat(pulse, "scaleX", 1f, 1.3f, 1f)
            val scaleY = ObjectAnimator.ofFloat(pulse, "scaleY", 1f, 1.3f, 1f)
            val alpha = ObjectAnimator.ofFloat(pulse, "alpha", 0.6f, 0.2f, 0.6f)

            pulseAnimator = AnimatorSet().apply {
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

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    private fun startRecording() {
        try {
            audioFile = File.createTempFile("breeze_recording_", ".pcm", activity.cacheDir)

            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Permission not granted")
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
        }
    }

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
                Log.e(TAG, "Error closing output stream", e)
            }
        }
    }

    private fun stopRecording() {
        try {
            isRecording = false
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            recordingThread?.join()
            recordingThread = null

            Log.d(TAG, "Recording stopped")

            // Transition to processing state
            setState(State.PROCESSING)

            // Notify that recording is complete
            audioFile?.let { file ->
                onRecordingComplete(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            isRecording = false
        }
    }

    /**
     * Show the ASR completion check mark and transition to floating window.
     * Call this when ASR processing is complete.
     */
    fun showAsrComplete(onComplete: () -> Unit) {
        processingText?.text = "Processing complete!"

        // Hide cancel button and show check icon
        processingCancel?.visibility = View.GONE
        processingCheck?.visibility = View.VISIBLE

        // Dismiss after short delay and transition to floating window
        handler.postDelayed({
            dismiss()
            onComplete()
        }, 1000)
    }

    /**
     * Update processing text (e.g., for streaming progress) with auto-scroll
     */
    fun updateProcessingText(text: String) {
        processingText?.text = text
        // Auto-scroll to bottom to show latest content
        processingScroll?.post {
            processingScroll?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun showKeyboard() {
        textInput?.let { input ->
            input.requestFocus()
            handler.postDelayed({
                val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }, 100)
        }
    }

    private fun hideKeyboard() {
        textInput?.let { input ->
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(input.windowToken, 0)
        }
    }

    fun dismiss() {
        cleanup()
        popupWindow?.dismiss()
        popupWindow = null
    }

    fun isShowing(): Boolean = popupWindow?.isShowing == true

    private fun cleanup() {
        stopPulseAnimation()
        removeLayoutListener()
        pendingRecordingStart = false
        handler.removeCallbacksAndMessages(null)
        if (isRecording) {
            isRecording = false
            try {
                audioRecord?.stop()
                audioRecord?.release()
                recordingThread?.join()
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up recorder", e)
            }
            audioRecord = null
            recordingThread = null
        }
        hideKeyboard()
        rootView = null
        anchorView = null
    }
}
