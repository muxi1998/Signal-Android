package com.mtkresearch.breeze.ui

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
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

    // Text input views
    private var textInput: EditText? = null
    private var sendButton: ImageButton? = null

    // Recording
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false

    // Permission handling
    private var pendingRecordingStart = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    // Colors for send button
    private val colorDisabled = android.graphics.Color.WHITE
    private val colorEnabled = android.graphics.Color.parseColor("#FF6B4E") // Orange

    fun show() {
        val rootView = activity.window.decorView.findViewById<View>(android.R.id.content)
        if (rootView == null) {
            Log.e(TAG, "Cannot show popup - no root view")
            return
        }

        // Hide keyboard naturally (no forced timing)
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        activity.currentFocus?.let { focus ->
            imm.hideSoftInputFromWindow(focus.windowToken, 0)
        }

        showPopupAtPosition(rootView)
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
            val color = if (hasText) colorEnabled else colorDisabled
            button.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
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
                processingCheck?.alpha = 0f
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
            audioFile = File.createTempFile("breeze_recording_", ".m4a", activity.cacheDir)

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(activity)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            Log.d(TAG, "Recording started: ${audioFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            isRecording = false
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

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

        // Animate check mark from transparent to solid green
        processingCheck?.let { check ->
            ObjectAnimator.ofFloat(check, "alpha", 0f, 1f).apply {
                duration = 300
                start()
            }
        }

        // Dismiss after 2 seconds
        handler.postDelayed({
            dismiss()
            onComplete()
        }, 2000)
    }

    /**
     * Update processing text (e.g., for streaming progress)
     */
    fun updateProcessingText(text: String) {
        processingText?.text = text
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
        pendingRecordingStart = false
        handler.removeCallbacksAndMessages(null)
        if (isRecording) {
            try {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up recorder", e)
            }
            mediaRecorder = null
            isRecording = false
        }
        hideKeyboard()
    }
}
