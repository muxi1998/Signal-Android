package com.mtkresearch.securesms.breeze.ui

import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.signal.core.util.logging.Log
import com.mtkresearch.securesms.breeze.AISession
import com.mtkresearch.securesms.breeze.ToneType
import com.mtkresearch.securesms.breeze.WindowPreferences
import kotlin.math.max

/**
 * Simplified floating window following enhanced spec:
 * - White @ 20-30% opacity with orange glow
 * - 82-88% screen width
 * - Previous Summary + Current Suggestion + Tone Chips
 * - Curved pill resize grip (26-32dp)
 * - Slide-to-accept/dismiss gestures
 */
class BreezeFloatingWindow private constructor(
  private val context: Context,
  private val anchorBounds: Rect,
  private val savedSettings: WindowPreferences.WindowSettings,
  private var session: AISession,
  private val onAccept: () -> Unit,
  private val onDismiss: () -> Unit,
  private val onResize: (Int, Int, Int, Int) -> Unit,
  private val onMove: (Int, Int, Int, Int) -> Unit,
  private val onToneChange: (ToneType) -> Unit
) {

  companion object {
    private val TAG = Log.tag(BreezeFloatingWindow::class.java)

    fun create(
      context: Context,
      anchorBounds: Rect,
      savedSettings: WindowPreferences.WindowSettings,
      session: AISession,
      onAccept: () -> Unit,
      onDismiss: () -> Unit,
      onResize: (Int, Int, Int, Int) -> Unit,
      onMove: (Int, Int, Int, Int) -> Unit,
      onToneChange: (ToneType) -> Unit
    ): BreezeFloatingWindow {
      return BreezeFloatingWindow(
        context, anchorBounds, savedSettings, session,
        onAccept, onDismiss, onResize, onMove, onToneChange
      )
    }
  }

  private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  private var windowView: BreezeWindowView? = null
  private var layoutParams: WindowManager.LayoutParams? = null
  private var keyboardHeight = 0
  private var originalY = 0

  fun show() {
    remove() // Ensure clean state

    windowView = BreezeWindowView(
      context,
      session,
      onAccept,
      onDismiss,
      onResize,
      onMove,
      onToneChange,
      ::updateWindowPosition,
      ::updateWindowSize,
      ::onSlideTemporaryMove
    )
    layoutParams = createLayoutParams()
    originalY = layoutParams?.y ?: 0

    windowManager.addView(windowView, layoutParams)
    windowView?.animateIn()
    
    // Setup keyboard detection per spec (Line 188)
    setupKeyboardDetection()
  }

  fun remove() {
    windowView?.let { view ->
      if (view.isAttachedToWindow) {
        windowManager.removeView(view)
      }
    }
    windowView = null
    layoutParams = null
  }

  fun updateSession(newSession: AISession) {
    session = newSession
    windowView?.updateSession(newSession)
  }

  // Temporary movement for slide gestures only
  private fun onSlideTemporaryMove(offsetY: Int) {
    layoutParams?.let { params ->
      val tempY = originalY + offsetY
      params.y = tempY
      
      windowView?.let { view ->
        if (view.isAttachedToWindow) {
          windowManager.updateViewLayout(view, params)
        }
      }
    }
  }
  
  internal fun updateWindowPosition(deltaX: Int, deltaY: Int) {
    Log.d("BreezeFloatingWindow", "==== updateWindowPosition DEBUG ====")
    Log.d("BreezeFloatingWindow", "Called with deltaX=$deltaX, deltaY=$deltaY")
    
    layoutParams?.let { params ->
      val oldX = params.x
      val oldY = params.y
      
      // Apply delta movement
      val newX = params.x + deltaX
      val newY = params.y + deltaY
      
      Log.d("BreezeFloatingWindow", "Current position: ($oldX, $oldY)")
      Log.d("BreezeFloatingWindow", "Requested new position: ($newX, $newY)")
      Log.d("BreezeFloatingWindow", "Window dimensions: ${params.width}x${params.height}")
      
      // Get display metrics for constraints
      val displayMetrics = context.resources.displayMetrics
      val screenWidth = displayMetrics.widthPixels
      val screenHeight = displayMetrics.heightPixels
      
      Log.d("BreezeFloatingWindow", "Screen dimensions: ${screenWidth}x$screenHeight")
      Log.d("BreezeFloatingWindow", "Keyboard height: $keyboardHeight")
      
      // Calculate constraints with detailed logging
      val margin = 16.dpToPx()
      val statusBarHeight = 48.dpToPx()
      
      // Horizontal constraints
      val minX = margin
      val maxX = screenWidth - params.width - margin
      
      // Vertical constraints - ensure valid bounds
      val minY = statusBarHeight
      
      // Calculate maxY with sanity checks
      val effectiveKeyboardHeight = if (keyboardHeight > 0 && keyboardHeight < screenHeight * 0.7) {
        keyboardHeight // Use keyboard height if reasonable
      } else {
        0 // Ignore unreasonable keyboard heights
      }
      
      val maxY = if (effectiveKeyboardHeight > 0) {
        // When keyboard visible, stay above it
        screenHeight - effectiveKeyboardHeight - params.height - margin
      } else {
        // When no keyboard, allow movement to bottom with margin
        screenHeight - params.height - margin
      }
      
      // Ensure maxY is never less than minY
      val validMaxY = kotlin.math.max(minY + 10, maxY)
      
      Log.d("BreezeFloatingWindow", "Calculated constraints:")
      Log.d("BreezeFloatingWindow", "  X bounds: $minX to $maxX (range: ${maxX - minX})")
      Log.d("BreezeFloatingWindow", "  Y bounds: $minY to $validMaxY (range: ${validMaxY - minY})")
      Log.d("BreezeFloatingWindow", "  Effective keyboard height: $effectiveKeyboardHeight")
      Log.d("BreezeFloatingWindow", "  New X: $newX -> constrained range check: $newX in [$minX, $maxX]")
      Log.d("BreezeFloatingWindow", "  New Y: $newY -> constrained range check: $newY in [$minY, $validMaxY]")
      
      // Check if the new position is being constrained
      val willConstrainX = newX < minX || newX > maxX
      val willConstrainY = newY < minY || newY > validMaxY
      
      if (willConstrainX) {
        Log.w("BreezeFloatingWindow", "X position will be constrained! newX=$newX not in [$minX, $maxX]")
      }
      
      if (willConstrainY) {
        Log.w("BreezeFloatingWindow", "Y position will be constrained! newY=$newY not in [$minY, $validMaxY]")
        if (newY > validMaxY) {
          Log.w("BreezeFloatingWindow", "Downward movement blocked: newY=$newY > validMaxY=$validMaxY")
        }
        if (newY < minY) {
          Log.w("BreezeFloatingWindow", "Upward movement blocked: newY=$newY < minY=$minY")
        }
      }
      
      // Apply constraints
      val constrainedX = kotlin.math.max(minX, kotlin.math.min(newX, maxX))
      val constrainedY = kotlin.math.max(minY, kotlin.math.min(newY, validMaxY))
      
      params.x = constrainedX
      params.y = constrainedY
      
      Log.d("BreezeFloatingWindow", "Final position: (${params.x}, ${params.y})")
      Log.d("BreezeFloatingWindow", "Movement applied: X: $oldX -> ${params.x} (delta: ${params.x - oldX})")
      Log.d("BreezeFloatingWindow", "Movement applied: Y: $oldY -> ${params.y} (delta: ${params.y - oldY})")
      
      windowView?.let { view ->
        if (view.isAttachedToWindow) {
          Log.d("BreezeFloatingWindow", "Calling windowManager.updateViewLayout...")
          windowManager.updateViewLayout(view, params)
          Log.d("BreezeFloatingWindow", "windowManager.updateViewLayout completed")
        } else {
          Log.e("BreezeFloatingWindow", "View not attached to window! Cannot update position")
        }
      }
      
      // Save position for persistence
      onMove(params.x, params.y, params.width, params.height)
      Log.d("BreezeFloatingWindow", "Position saved to preferences")
    } ?: Log.e("BreezeFloatingWindow", "layoutParams is null!")
    
    Log.d("BreezeFloatingWindow", "==== updateWindowPosition DEBUG END ====")
  }
  
  private fun setupKeyboardDetection() {
    windowView?.let { view ->
      val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        // Get the visible display frame
        val rect = Rect()
        view.getWindowVisibleDisplayFrame(rect)
        
        val displayMetrics = context.resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        
        // Calculate keyboard height based on visible frame
        val visibleHeight = rect.bottom - rect.top
        val keyboardHeight = screenHeight - rect.bottom
        
        Log.d("BreezeFloatingWindow", "Keyboard detection: screen=$screenHeight, visible=$visibleHeight, keyboard=$keyboardHeight")
        
        // Only consider it a keyboard if it's significant (> 15% of screen)
        val newKeyboardHeight = if (keyboardHeight > screenHeight * 0.15) {
          keyboardHeight
        } else {
          0
        }
        
        if (newKeyboardHeight != this@BreezeFloatingWindow.keyboardHeight) {
          Log.d("BreezeFloatingWindow", "Keyboard height changed: ${this@BreezeFloatingWindow.keyboardHeight} -> $newKeyboardHeight")
          this@BreezeFloatingWindow.keyboardHeight = newKeyboardHeight
          // Adjust window position to avoid keyboard
          if (newKeyboardHeight > 0) {
            updateWindowPosition(0, 0) // Trigger position recalculation
          }
        }
      }
      view.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }
  }

  internal fun updateWindowSize(newWidth: Int, newHeight: Int) {
    layoutParams?.let { params ->
      Log.d("BreezeFloatingWindow", "updateWindowSize called: ${params.width}x${params.height} -> ${newWidth}x$newHeight")
      params.width = newWidth
      params.height = newHeight
      
      windowView?.let { view ->
        if (view.isAttachedToWindow) {
          windowManager.updateViewLayout(view, params)
          Log.d("BreezeFloatingWindow", "updateViewLayout called successfully")
        } else {
          Log.w("BreezeFloatingWindow", "Window not attached, cannot update layout")
        }
      }
      
      // Save the new size for persistence  
      onResize(params.width, params.height, params.x, params.y)
    } ?: Log.w("BreezeFloatingWindow", "layoutParams is null, cannot resize")
  }

  private fun createLayoutParams(): WindowManager.LayoutParams {
    val displayMetrics = context.resources.displayMetrics

    // Spec: 82-88% screen width
    val screenWidth = displayMetrics.widthPixels
    val targetWidth = (screenWidth * 0.85f).toInt() // 85% middle ground
    val minWidth = 280.dpToPx()
    val maxWidth = screenWidth - 16.dpToPx()

    val width = if (savedSettings.hasSavedPosition) {
      savedSettings.width.coerceIn(minWidth, maxWidth)
    } else {
      targetWidth.coerceIn(minWidth, maxWidth)
    }

    val height = if (savedSettings.hasSavedPosition) {
      // Constrain saved height to reasonable bounds
      val maxHeight = (displayMetrics.heightPixels * 0.5f).toInt() // Max 50% of screen
      val minHeight = 120.dpToPx()
      savedSettings.height.coerceIn(minHeight, maxHeight)
    } else {
      200.dpToPx() // Default height
    }

    val params = WindowManager.LayoutParams(
      width,
      height,
      getOverlayType(),
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT
    )

    params.gravity = Gravity.TOP or Gravity.START

    // Position above input field by 6-12dp (spec)
    if (savedSettings.hasSavedPosition) {
      params.x = savedSettings.x
      params.y = savedSettings.y
    } else {
      params.x = anchorBounds.left
      params.y = anchorBounds.top - height - 8.dpToPx() // 8dp spacing
    }

    return params
  }

  private fun getOverlayType(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
      @Suppress("DEPRECATION")
      WindowManager.LayoutParams.TYPE_PHONE
    }
  }

  private fun Int.dpToPx(): Int {
    return (this * context.resources.displayMetrics.density).toInt()
  }
}

/**
 * Main window view with spec-compliant styling and components.
 */
private class BreezeWindowView(
  context: Context,
  private var session: AISession,
  private val onAccept: () -> Unit,
  private val onDismiss: () -> Unit,
  private val onResize: (Int, Int, Int, Int) -> Unit,
  private val onMove: (Int, Int, Int, Int) -> Unit,
  private val onToneChange: (ToneType) -> Unit,
  private val onMoveWindow: (Int, Int) -> Unit,
  private val onResizeWindow: (Int, Int) -> Unit,
  private val onSlideTemporaryMove: (Int) -> Unit
) : FrameLayout(context) {

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val cornerRadius = 22.dpToPx().toFloat() // 20-24dp per spec

  // Touch handling for drag and invisible resize
  private var lastTouchX = 0f
  private var lastTouchY = 0f
  private var isDragging = false
  private var isResizing = false
  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
  private val resizeArea = 50.dpToPx() // Invisible resize area in bottom-right

  // UI components
  private lateinit var previousSummaryView: TextView
  private lateinit var currentSuggestionView: TextView
  private lateinit var toneChipsContainer: LinearLayout
  private lateinit var slideHandle: SlideHandleView

  init {
    setupWindow()
    setupComponents()
    updateContent()
  }

  private fun setupWindow() {
    setWillNotDraw(false)
    isClickable = true
    isFocusable = true

    // Enhanced shadow per spec: 0 8px 32px rgba(0,0,0,0.25–0.3)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      elevation = 24.dpToPx().toFloat() // Increased for more prominent shadow
      outlineProvider = ViewOutlineProvider.BACKGROUND
      clipToOutline = false // Allow shadow to extend beyond bounds
    }

    // Setup glassmorphic background per spec
    paint.apply {
      // Much higher opacity for excellent readability
      color = Color.argb(240, 255, 255, 255) // ~94% white opacity - maximum readability
      style = Paint.Style.FILL
    }
    
    Log.d("BreezeFloatingWindow", "Window view created with clickable=${isClickable}, focusable=${isFocusable}")
  }

  private fun setupComponents() {
    // Main content scroll view
    val scrollView = ScrollView(context).apply {
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
        setMargins(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 60.dpToPx()) // Leave space for slide handle
      }
      isVerticalScrollBarEnabled = false
    }

    val contentLayout = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    // Previous Summary (collapsible per spec)
    previousSummaryView = TextView(context).apply {
      textSize = 12f
      setTextColor(Color.argb(220, 0, 0, 0)) // Dark text for better readability
      maxLines = 1
      layoutParams = LinearLayout.LayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.WRAP_CONTENT
      ).apply {
        bottomMargin = 12.dpToPx()
      }
      setOnClickListener { toggleSummaryExpanded() }
    }
    contentLayout.addView(previousSummaryView)

    // Current AI Suggestion with long press to copy
    currentSuggestionView = TextView(context).apply {
      textSize = 16f
      setTextColor(Color.argb(255, 0, 0, 0)) // Black text for better readability
      layoutParams = LinearLayout.LayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.WRAP_CONTENT
      ).apply {
        bottomMargin = 16.dpToPx()
      }
      
      // Implement long press to copy per spec (Line 148)
      setOnLongClickListener {
        val text = this.text.toString()
        if (text.isNotBlank()) {
          val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
          val clip = ClipData.newPlainText("AI Suggestion", text)
          clipboard.setPrimaryClip(clip)
          
          // Show feedback
          Toast.makeText(context, "Suggestion copied", Toast.LENGTH_SHORT).show()
          
          // Visual feedback - brief highlight
          val originalColor = textColors
          setTextColor(Color.argb(255, 255, 140, 0)) // Orange highlight
          Handler(Looper.getMainLooper()).postDelayed({
            setTextColor(originalColor)
          }, 150)
        }
        true
      }
    }
    contentLayout.addView(currentSuggestionView)

    // Tone/Clarity Chips (per spec)
    toneChipsContainer = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      layoutParams = LinearLayout.LayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.WRAP_CONTENT
      )
    }
    createToneChips()
    contentLayout.addView(toneChipsContainer)

    scrollView.addView(contentLayout)
    addView(scrollView)

    // Slide Handle at bottom - centered but not full width to allow drag/resize
    slideHandle = SlideHandleView(context, ::handleSlideGesture)
    slideHandle.layoutParams = LayoutParams(
      200.dpToPx(), // Fixed width to leave space for drag/resize
      36.dpToPx()
    ).apply {
      gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    }
    // Setup window movement callback for slide gestures (spec Line 160)
    slideHandle.setWindowMoveCallback { offsetY ->
      onSlideTemporaryMove(offsetY.toInt())
    }
    slideHandle.setBackgroundColor(Color.TRANSPARENT)
    slideHandle.isClickable = true // Re-enable slide handle
    addView(slideHandle)
  }

  private fun createToneChips() {
    // Spec: Tone/Clarity chips trigger new AI refinement (lines 147, 320-322)
    val toneTypes = listOf(
      ToneType.FORMAL to "Formal",
      ToneType.FRIENDLY to "Friendly", 
      ToneType.CLARITY to "Clear",
      ToneType.SHORTEN to "Short",
      ToneType.EXPAND to "Expand"
    )

    toneTypes.forEachIndexed { index, (type, label) ->
      val chip = TextView(context).apply {
        text = label
        textSize = 13f // Slightly larger for better touch target
        setTextColor(Color.argb(255, 0, 0, 0)) // Black text for readability
        
        // Enhanced chip styling for better visibility and touch
        background = createChipBackground()
        setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx()) // Larger padding for better touch
        
        layoutParams = LinearLayout.LayoutParams(
          LayoutParams.WRAP_CONTENT,
          LayoutParams.WRAP_CONTENT
        ).apply {
          rightMargin = if (index < toneTypes.size - 1) 10.dpToPx() else 0 // Spacing between chips
          topMargin = 4.dpToPx()
          bottomMargin = 4.dpToPx()
        }
        
        // Robust click handling per spec (tap chips → trigger new AI refinement)
        setOnClickListener { view ->
          Log.d("BreezeFloatingWindow", "=== TONE CHIP CLICKED ===")
          Log.d("BreezeFloatingWindow", "Chip: $label ($type)")
          Log.d("BreezeFloatingWindow", "View: $view")
          Log.d("BreezeFloatingWindow", "Clickable: ${view.isClickable}")
          Log.d("BreezeFloatingWindow", "Enabled: ${view.isEnabled}")
          
          try {
            // Trigger tone change with visual feedback
            view.alpha = 0.6f
            view.postDelayed({
              view.alpha = 1.0f
            }, 100)
            
            onToneChange(type)
            Log.d("BreezeFloatingWindow", "Tone change triggered successfully for $type")
          } catch (e: Exception) {
            Log.e("BreezeFloatingWindow", "Error triggering tone change for $type", e)
          }
        }
        
        // Enhanced touch properties for reliability
        isClickable = true
        isFocusable = true
        isEnabled = true
        
        // Add touch feedback
        setOnTouchListener { view, event ->
          when (event.action) {
            MotionEvent.ACTION_DOWN -> {
              Log.d("BreezeFloatingWindow", "Tone chip touch DOWN: $label")
              view.alpha = 0.7f
              false // Allow click to proceed
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
              Log.d("BreezeFloatingWindow", "Tone chip touch UP/CANCEL: $label")
              view.alpha = 1.0f
              false // Allow click to proceed
            }
            else -> false
          }
        }
        
        Log.d("BreezeFloatingWindow", "Created tone chip: $label (clickable=$isClickable, enabled=$isEnabled)")
      }
      
      toneChipsContainer.addView(chip)
      Log.d("BreezeFloatingWindow", "Added tone chip $index to container: $label")
    }
    
    Log.d("BreezeFloatingWindow", "Tone chips container setup complete. Child count: ${toneChipsContainer.childCount}")
  }

  fun updateSession(newSession: AISession) {
    session = newSession
    updateContent()
  }

  private fun updateContent() {
    // Previous Summary
    if (session.previousSummary.isNotBlank()) {
      previousSummaryView.text = "Previous: ${session.previousSummary}"
      previousSummaryView.visibility = View.VISIBLE
    } else {
      previousSummaryView.visibility = View.GONE
    }

    // Current Suggestion
    currentSuggestionView.text = session.currentSuggestion
  }

  private fun toggleSummaryExpanded() {
    previousSummaryView.maxLines = if (previousSummaryView.maxLines == 1) Int.MAX_VALUE else 1
  }

  override fun onDraw(canvas: Canvas) {
    // Create rounded rectangle path for glassmorphic background
    val path = Path().apply {
      addRoundRect(
        0f,
        0f,
        width.toFloat(),
        height.toFloat(),
        cornerRadius,
        cornerRadius,
        Path.Direction.CW
      )
    }

    // Enhanced glassmorphic background with subtle blur simulation
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.argb(240, 255, 255, 255) // High opacity for readability
      style = Paint.Style.FILL
      // Add subtle texture for depth
      shader = LinearGradient(
        0f, 0f, 0f, height.toFloat(),
        intArrayOf(
          Color.argb(245, 255, 255, 255),
          Color.argb(235, 255, 255, 255)
        ),
        null,
        Shader.TileMode.CLAMP
      )
    }
    canvas.drawPath(path, backgroundPaint)

    // Enhanced orange glow border per spec (8-12% opacity)
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.argb(40, 255, 140, 0) // 8-12% orange glow per spec
      style = Paint.Style.STROKE
      strokeWidth = 2f
      // Add glow effect
      maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.OUTER)
    }
    canvas.drawPath(path, glowPaint)

    // Multiple glow layers for premium effect
    val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.argb(20, 255, 140, 0)
      style = Paint.Style.STROKE
      strokeWidth = 4f
      maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.OUTER)
    }
    canvas.drawPath(path, outerGlowPaint)

    // Inner highlight for glassmorphic effect
    val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.argb(60, 255, 255, 255)
      style = Paint.Style.STROKE
      strokeWidth = 1f
    }
    val innerPath = Path().apply {
      addRoundRect(
        1f,
        1f,
        width.toFloat() - 1f,
        height.toFloat() - 1f,
        cornerRadius - 1f,
        cornerRadius - 1f,
        Path.Direction.CW
      )
    }
    canvas.drawPath(innerPath, highlightPaint)

    super.onDraw(canvas)
  }

  // drawResizeGrip function removed per user request

  override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
    // Important: For overlay windows, we need to be careful about touch interception
    // Only intercept if we're sure we want to handle the touch
    
    when (ev.action) {
      MotionEvent.ACTION_DOWN -> {
        Log.d("BreezeFloatingWindow", "onInterceptTouchEvent DOWN at local(${ev.x}, ${ev.y}) raw(${ev.rawX}, ${ev.rawY})")
        
        // Check if touch is on clickable children that need to handle their own events
        val clickableChild = findClickableChildAt(ev.x, ev.y)
        if (clickableChild != null) {
          Log.d("BreezeFloatingWindow", "Touch on clickable: ${clickableChild.javaClass.simpleName}")
          return false // Don't intercept - let child handle
        }
        
        // Check if touch is on slide handle
        if (::slideHandle.isInitialized && isTouchOnSlideHandle(ev.x, ev.y)) {
          Log.d("BreezeFloatingWindow", "Touch on slide handle")
          return false // Let slide handle handle its own touch
        }
        
        // For all other areas (drag or resize), we'll handle in onTouchEvent
        Log.d("BreezeFloatingWindow", "Touch in window area - will handle in onTouchEvent")
        return false // Don't intercept yet, let onTouchEvent decide
      }
      
      MotionEvent.ACTION_MOVE -> {
        // Don't intercept MOVE events - let them flow to onTouchEvent
        return false
      }
    }
    
    return false
  }
  
  private fun findClickableChildAt(x: Float, y: Float): View? {
    // Priority 1: Check tone chips (most important for spec compliance)
    if (::toneChipsContainer.isInitialized && toneChipsContainer.visibility == View.VISIBLE) {
      val containerBounds = Rect()
      toneChipsContainer.getHitRect(containerBounds)
      
      Log.d("BreezeFloatingWindow", "Checking tone chips container bounds: $containerBounds")
      Log.d("BreezeFloatingWindow", "Touch coordinates: (${x.toInt()}, ${y.toInt()})")
      
      if (containerBounds.contains(x.toInt(), y.toInt())) {
        Log.d("BreezeFloatingWindow", "Touch is within tone chips container")
        
        // Check each tone chip
        for (i in 0 until toneChipsContainer.childCount) {
          val child = toneChipsContainer.getChildAt(i)
          if (child.visibility == View.VISIBLE && child.isClickable) {
            val childBounds = Rect()
            child.getHitRect(childBounds)
            // Convert child bounds to container coordinate system
            childBounds.offset(containerBounds.left, containerBounds.top)
            
            Log.d("BreezeFloatingWindow", "Checking tone chip $i bounds: $childBounds")
            
            if (childBounds.contains(x.toInt(), y.toInt())) {
              Log.d("BreezeFloatingWindow", "Found tone chip $i at touch location!")
              return child
            }
          }
        }
      }
    }
    
    // Priority 2: Check other clickable elements like summary text
    if (::previousSummaryView.isInitialized && previousSummaryView.visibility == View.VISIBLE) {
      val summaryBounds = Rect()
      previousSummaryView.getHitRect(summaryBounds)
      if (summaryBounds.contains(x.toInt(), y.toInt())) {
        Log.d("BreezeFloatingWindow", "Found summary text at touch location")
        return previousSummaryView
      }
    }
    
    // Priority 3: Check current suggestion for long press (copy functionality)
    if (::currentSuggestionView.isInitialized && currentSuggestionView.visibility == View.VISIBLE) {
      val suggestionBounds = Rect()
      currentSuggestionView.getHitRect(suggestionBounds)
      if (suggestionBounds.contains(x.toInt(), y.toInt())) {
        Log.d("BreezeFloatingWindow", "Found suggestion text at touch location")
        return currentSuggestionView
      }
    }
    
    return null
  }
  
  private fun isTouchOnSlideHandle(x: Float, y: Float): Boolean {
    if (!::slideHandle.isInitialized || slideHandle.visibility != View.VISIBLE) {
      return false
    }
    
    val handleBounds = Rect()
    slideHandle.getHitRect(handleBounds)
    val result = handleBounds.contains(x.toInt(), y.toInt())
    
    if (result) {
      Log.d("BreezeFloatingWindow", "Touch confirmed on slide handle: bounds=$handleBounds, touch=(${x.toInt()}, ${y.toInt()})")
    }
    
    return result
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    val actionName = when(event.action) {
      MotionEvent.ACTION_DOWN -> "DOWN"
      MotionEvent.ACTION_MOVE -> "MOVE" 
      MotionEvent.ACTION_UP -> "UP"
      MotionEvent.ACTION_CANCEL -> "CANCEL"
      else -> "OTHER(${event.action})"
    }
    
    // Quick log for high-frequency MOVE events
    if (event.action == MotionEvent.ACTION_MOVE) {
      Log.d("BreezeFloatingWindow", "MOVE: raw(${event.rawX.toInt()}, ${event.rawY.toInt()}) drag=$isDragging resize=$isResizing")
    } else {
      Log.d("BreezeFloatingWindow", "=== onTouchEvent: $actionName ===")
      Log.d("BreezeFloatingWindow", "Local: (${event.x}, ${event.y}) Raw: (${event.rawX}, ${event.rawY})")
      Log.d("BreezeFloatingWindow", "States: dragging=$isDragging, resizing=$isResizing")
    }
    
    when (event.action) {
      MotionEvent.ACTION_DOWN -> {
        // Store initial touch position - CRITICAL to use rawX/rawY for window movement
        lastTouchX = event.rawX
        lastTouchY = event.rawY

        // Determine interaction mode based on touch location
        val resizeArea = 50.dpToPx()
        val inResizeArea = event.x >= width - resizeArea && event.y >= height - resizeArea
        
        // Force dragging mode if not in resize area
        if (inResizeArea) {
          isResizing = true
          isDragging = false
          Log.d("BreezeFloatingWindow", "Mode: RESIZE (touch in bottom-right corner)")
        } else {
          isResizing = false
          isDragging = true
          Log.d("BreezeFloatingWindow", "Mode: DRAG (touch outside resize area)")
        }
        
        Log.d("BreezeFloatingWindow", "Initial touch: lastTouch=(${lastTouchX}, ${lastTouchY})")
        return true
      }

      MotionEvent.ACTION_MOVE -> {
        if (!isDragging && !isResizing) {
          Log.w("BreezeFloatingWindow", "MOVE without drag/resize state! Forcing drag mode")
          isDragging = true
        }
        
        // Calculate movement delta using RAW coordinates
        val deltaX = event.rawX - lastTouchX
        val deltaY = event.rawY - lastTouchY
        
        // Only log significant movements to reduce spam
        if (kotlin.math.abs(deltaX) > 2 || kotlin.math.abs(deltaY) > 2) {
          Log.d("BreezeFloatingWindow", "Delta: ($deltaX, $deltaY) from last=(${lastTouchX}, ${lastTouchY}) to current=(${event.rawX}, ${event.rawY})")
        }

        if (isResizing) {
          val currentWidth = layoutParams?.width ?: width
          val currentHeight = layoutParams?.height ?: height
          
          val minWidth = 280.dpToPx()
          val minHeight = 120.dpToPx()
          val newWidth = kotlin.math.max(minWidth, (currentWidth + deltaX).toInt())
          val newHeight = kotlin.math.max(minHeight, (currentHeight + deltaY).toInt())
          
          if (newWidth != currentWidth || newHeight != currentHeight) {
            Log.d("BreezeFloatingWindow", "RESIZE: ${currentWidth}x$currentHeight -> ${newWidth}x$newHeight")
            onResizeWindow(newWidth, newHeight)
          }
        } else if (isDragging) {
          // Use floating point for smoother movement
          val dx = deltaX.toInt()
          val dy = deltaY.toInt()
          
          // Lower threshold for more responsive movement
          if (kotlin.math.abs(deltaX) > 0.5 || kotlin.math.abs(deltaY) > 0.5) {
            Log.d("BreezeFloatingWindow", "DRAG: Moving by ($dx, $dy)")
            
            // Direct call to move window
            try {
              onMoveWindow(dx, dy)
            } catch (e: Exception) {
              Log.e("BreezeFloatingWindow", "Error in onMoveWindow", e)
            }
          }
        } else {
          Log.w("BreezeFloatingWindow", "MOVE event but neither dragging nor resizing!")
        }

        // CRITICAL: Update last touch position for next delta
        lastTouchX = event.rawX
        lastTouchY = event.rawY
        return true
      }

      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        Log.d("BreezeFloatingWindow", "Touch $actionName: Resetting states")
        isDragging = false
        isResizing = false
        return true
      }
    }

    return false
  }

  private fun handleSlideGesture(direction: SlideDirection) {
    // Trigger enhanced animation first
    animateSlideAction(direction)
    
    // Delay actual action to allow animation to play
    val delay = when (direction) {
      SlideDirection.DOWN -> 200L // Faster for accept
      SlideDirection.UP -> 150L   // Faster for reject
      SlideDirection.NONE -> 0L   // Immediate for cancel
    }
    
    if (delay > 0) {
      Handler(Looper.getMainLooper()).postDelayed({
        when (direction) {
          SlideDirection.DOWN -> onAccept()
          SlideDirection.UP -> onDismiss()
          SlideDirection.NONE -> { /* No action */ }
        }
      }, delay)
    }
  }

  fun animateIn() {
    // Enhanced pop-in animation per spec (Lines 194-198)
    scaleX = 0.92f
    scaleY = 0.92f
    alpha = 0f

    // Multi-stage animation for premium feel
    animate()
      .scaleX(1.05f) // Slight overshoot for elastic feel
      .scaleY(1.05f)
      .alpha(1f)
      .setDuration(150)
      .setInterpolator(AccelerateDecelerateInterpolator())
      .withEndAction {
        // Settle back to normal size with elastic interpolation
        animate()
          .scaleX(1f)
          .scaleY(1f)
          .setDuration(100)
          .setInterpolator(OvershootInterpolator(0.3f))
          .start()
      }
      .start()
  }
  
  // Enhanced slide animation for accept/dismiss
  fun animateSlideAction(direction: SlideDirection) {
    when (direction) {
      SlideDirection.DOWN -> {
        // Accept animation - fade out with scale down
        animate()
          .alpha(0f)
          .scaleX(0.95f)
          .scaleY(0.95f)
          .translationY(50.dpToPx().toFloat())
          .setDuration(300)
          .setInterpolator(AccelerateDecelerateInterpolator())
          .start()
      }
      SlideDirection.UP -> {
        // Reject animation - move up and shrink per spec (Line 168-169)
        animate()
          .alpha(0f)
          .scaleX(0.8f)
          .scaleY(0.8f)
          .translationY(-100.dpToPx().toFloat())
          .setDuration(250)
          .setInterpolator(AccelerateDecelerateInterpolator())
          .start()
      }
      SlideDirection.NONE -> {
        // Return to original position with elastic bounce
        animate()
          .translationY(0f)
          .setDuration(200)
          .setInterpolator(OvershootInterpolator(0.5f))
          .start()
      }
    }
  }

  private fun createChipBackground(): GradientDrawable {
    return GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      cornerRadius = 12.dpToPx().toFloat()
      setColor(Color.argb(200, 255, 165, 0)) // More opaque orange background
      setStroke(2, Color.argb(255, 255, 140, 0)) // Stronger orange border
    }
  }

  private fun Int.dpToPx(): Int {
    return (this * context.resources.displayMetrics.density).toInt()
  }
}

/**
 * Enhanced slide handle with window-follows-finger animation.
 */
private class SlideHandleView(
  context: Context,
  private val onSlide: (SlideDirection) -> Unit
) : View(context) {

  private var startY = 0f
  private var isDragging = false
  private var currentOffset = 0f
  private val sliderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.argb(180, 255, 255, 255) // More visible white handle
    style = Paint.Style.FILL
  }
  
  // Callback to move parent window during slide
  private var onWindowMove: ((Float) -> Unit)? = null
  
  fun setWindowMoveCallback(callback: (Float) -> Unit) {
    onWindowMove = callback
  }

  override fun onDraw(canvas: Canvas) {
    // Draw simple slider handle - make it more visible
    val handleWidth = 50.dpToPx().toFloat() // Wider
    val handleHeight = 6.dpToPx().toFloat() // Taller
    val handleX = (width - handleWidth) / 2
    val handleY = (height - handleHeight) / 2
    
    // Draw background for better visibility
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.argb(80, 0, 0, 0) // Semi-transparent dark background
      style = Paint.Style.FILL
    }
    val bgRect = RectF(handleX - 4, handleY - 2, handleX + handleWidth + 4, handleY + handleHeight + 2)
    canvas.drawRoundRect(bgRect, (handleHeight + 4) / 2, (handleHeight + 4) / 2, bgPaint)

    canvas.drawRoundRect(
      handleX,
      handleY,
      handleX + handleWidth,
      handleY + handleHeight,
      handleHeight / 2,
      handleHeight / 2,
      sliderPaint
    )
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    Log.d("BreezeFloatingWindow", "SlideHandleView.onTouchEvent: action=${event.action} at (${event.x}, ${event.y})")
    when (event.action) {
      MotionEvent.ACTION_DOWN -> {
        startY = event.y
        isDragging = true
        return true
      }

      MotionEvent.ACTION_MOVE -> {
        if (isDragging) {
          val deltaY = event.y - startY
          currentOffset = deltaY
          
          // Window follows finger per spec (Line 160)
          onWindowMove?.invoke(deltaY * 0.3f) // Dampened movement for smooth feel
          
          // Visual feedback - change handle appearance based on direction
          val alpha = kotlin.math.min(255, (180 + kotlin.math.abs(deltaY) * 2).toInt())
          sliderPaint.color = Color.argb(alpha, 255, 255, 255)
          invalidate()
          
          return true
        }
      }

      MotionEvent.ACTION_UP -> {
        if (isDragging) {
          val deltaY = event.y - startY
          val threshold = 60.dpToPx() // Threshold per spec (40-60px)

          val direction = when {
            deltaY > threshold -> SlideDirection.DOWN
            deltaY < -threshold -> SlideDirection.UP
            else -> SlideDirection.NONE
          }

          // Reset window position if no action taken
          if (direction == SlideDirection.NONE) {
            onWindowMove?.invoke(0f) // Return to original position
          }
          
          onSlide(direction)
          
          // Reset visual state
          currentOffset = 0f
          sliderPaint.color = Color.argb(180, 255, 255, 255)
          invalidate()
        }
        isDragging = false
        return true
      }
    }

    return super.onTouchEvent(event)
  }

  private fun Int.dpToPx(): Int {
    return (this * context.resources.displayMetrics.density).toInt()
  }
}

enum class SlideDirection {
  NONE, UP, DOWN
}
