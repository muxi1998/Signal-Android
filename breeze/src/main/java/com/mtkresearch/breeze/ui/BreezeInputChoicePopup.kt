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

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    fun show() {
        val rootView = activity.window.decorView.findViewById<View>(android.R.id.content)
        if (rootView == null) {
            Log.e(TAG, "Cannot show popup - no root view")
            return
        }

        val inflater = LayoutInflater.from(activity)
        popupView = inflater.inflate(R.layout.breeze_input_choice_popup, null)

        setupViews()
        setupClickListeners()

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

        // Position popup above the anchor
        val x = anchorBounds.centerX() - 140 // Half of minWidth
        val y = anchorBounds.top - 150

        Log.d(TAG, "Showing popup at x=$x, y=$y")
        popupWindow?.showAtLocation(rootView, Gravity.NO_GRAVITY, x, y)

        setState(State.CHOICE)
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
                if (checkRecordPermission()) {
                    setState(State.RECORDING)
                    startRecording()
                } else {
                    requestRecordPermission()
                }
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
        Toast.makeText(activity, "Please grant microphone permission", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private val TAG = Log.tag(BreezeInputChoicePopup::class.java)
        private const val PERMISSION_REQUEST_CODE = 1001
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
