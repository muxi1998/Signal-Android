package com.mtkresearch.breeze.ui

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
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
import com.mtkresearch.breeze.AISession
import com.mtkresearch.breeze.ToneType
import com.mtkresearch.breeze.WindowPreferences
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
      anchorBounds,
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
  private val anchorBounds: Rect,
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

    // Tone/Clarity Chips with wrapping layout for better UX
    toneChipsContainer = createWrappingChipsLayout()
    createToneChips()
    contentLayout.addView(toneChipsContainer)

    scrollView.addView(contentLayout)
    addView(scrollView)

    // Slide Handle at bottom - centered but not full width to allow drag/resize
    slideHandle = SlideHandleView(context, ::handleSlideGesture)
    slideHandle.layoutParams = LayoutParams(
      200.dpToPx(), // Fixed width to leave space for drag/resize
      70.dpToPx() // Balanced height for hint visibility while keeping bar close to bottom
    ).apply {
      gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
      setMargins(0, 0, 0, 4.dpToPx()) // Small margin for visual balance
    }
    // Setup window movement callback for slide gestures (spec Line 160)
    slideHandle.setWindowMoveCallback { offsetY ->
      onSlideTemporaryMove(offsetY.toInt())
    }
    
    // Note: Contextual positioning will be set in animateIn() after layout
    
    slideHandle.setBackgroundColor(Color.TRANSPARENT)
    slideHandle.isClickable = true // Re-enable slide handle
    
    // Allow hints to draw outside container bounds
    clipChildren = false
    clipToPadding = false
    
    addView(slideHandle)
  }

  private fun createWrappingChipsLayout(): LinearLayout {
    // Create horizontal scroll view for compact single-row layout
    val scrollView = android.widget.HorizontalScrollView(context).apply {
      layoutParams = LinearLayout.LayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.WRAP_CONTENT
      )
      isHorizontalScrollBarEnabled = false // Hide scrollbar for cleaner UI
      isHorizontalFadingEdgeEnabled = true // Visual indicator for scrollable content
      setFadingEdgeLength(24.dpToPx()) // Subtle fade edge
      setPadding(4.dpToPx(), 0, 4.dpToPx(), 0) // Small padding for fade edge
    }
    
    val chipsContainer = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL // Single row layout
      layoutParams = LinearLayout.LayoutParams(
        LayoutParams.WRAP_CONTENT,
        LayoutParams.WRAP_CONTENT
      )
      setPadding(8.dpToPx(), 0, 8.dpToPx(), 0) // Padding so chips don't touch edges
    }
    
    scrollView.addView(chipsContainer)
    
    // Create a wrapper to hold the scroll view (since we need to return LinearLayout)
    val wrapper = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      layoutParams = LinearLayout.LayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.WRAP_CONTENT
      )
    }
    wrapper.addView(scrollView)
    
    // Store reference to the actual chips container for adding chips
    wrapper.tag = chipsContainer
    return wrapper
  }

  private fun createToneChips() {
    // Spec: Tone/Clarity chips trigger new AI refinement (lines 147, 320-322)
    val toneTypes = listOf(
      ToneType.HISTORY_JSON to "History",  // First option for AI preset
      ToneType.FORMAL to "Formal",
      ToneType.FRIENDLY to "Friendly", 
      ToneType.CLARITY to "Clear",
      ToneType.SHORTEN to "Short",
      ToneType.EXPAND to "Expand"
      // All options now available with horizontal scroll - no overflow issues!
    )

    // Get the actual chips container from the wrapper's tag
    val actualContainer = (toneChipsContainer.tag as? LinearLayout) ?: toneChipsContainer
    
    toneTypes.forEach { (type, label) ->
      val chip = TextView(context).apply {
        text = label
        textSize = 13f
        setTextColor(Color.argb(255, 0, 0, 0))
        background = createChipBackground()
        setPadding(14.dpToPx(), 6.dpToPx(), 14.dpToPx(), 6.dpToPx())
        
        layoutParams = LinearLayout.LayoutParams(
          LayoutParams.WRAP_CONTENT,
          LayoutParams.WRAP_CONTENT
        ).apply {
          rightMargin = 8.dpToPx() // Spacing between chips in horizontal layout
        }
        
        // Robust click handling per spec (tap chips → trigger new AI refinement)
        setOnClickListener { view ->
          Log.d("BreezeFloatingWindow", "=== TONE CHIP CLICKED ===")
          Log.d("BreezeFloatingWindow", "Chip: $label ($type)")
          
          try {
            // Trigger tone change with visual feedback
            view.alpha = 0.6f
            view.postDelayed({ view.alpha = 1.0f }, 100)
            onToneChange(type)
            Log.d("BreezeFloatingWindow", "Tone change triggered successfully for $type")
          } catch (e: Exception) {
            Log.e("BreezeFloatingWindow", "Error triggering tone change for $type", e)
          }
        }
        
        isClickable = true
        isFocusable = true
        isEnabled = true
      }
      
      actualContainer.addView(chip)
      Log.d("BreezeFloatingWindow", "Added chip '$label' to horizontal scroll container")
    }
    
    Log.d("BreezeFloatingWindow", "Horizontal scrollable tone chips setup complete. Total: ${toneTypes.size}")
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
        
        // For horizontal scroll layout, find the actual chips container
        val actualChipsContainer = findHorizontalChipsContainer(toneChipsContainer)
        if (actualChipsContainer != null) {
          val scrollBounds = Rect()
          actualChipsContainer.getHitRect(scrollBounds)
          scrollBounds.offset(containerBounds.left, containerBounds.top)
          
          // Check each chip in horizontal layout
          for (chipIndex in 0 until actualChipsContainer.childCount) {
            val chip = actualChipsContainer.getChildAt(chipIndex)
            if (chip.visibility == View.VISIBLE && chip.isClickable) {
              val chipBounds = Rect()
              chip.getHitRect(chipBounds)
              chipBounds.offset(scrollBounds.left, scrollBounds.top)
              
              if (chipBounds.contains(x.toInt(), y.toInt())) {
                Log.d("BreezeFloatingWindow", "Found chip at position $chipIndex in horizontal scroll")
                return chip
              }
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
  
  private fun findHorizontalChipsContainer(wrapper: LinearLayout): LinearLayout? {
    // The actual chips container is stored in the wrapper's tag
    return wrapper.tag as? LinearLayout
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
          .withEndAction {
            // Set contextual arrow text based on input field position
            val windowLocation = IntArray(2)
            this@BreezeWindowView.getLocationOnScreen(windowLocation)
            slideHandle.setInputFieldPosition(windowLocation[1], anchorBounds.top)
            
            // Add subtle pulse to slide handle to hint at interaction
            slideHandle.animate()
              .scaleX(1.1f)
              .scaleY(1.1f)
              .setDuration(400)
              .setInterpolator(AccelerateDecelerateInterpolator())
              .withEndAction {
                slideHandle.animate()
                  .scaleX(1f)
                  .scaleY(1f)
                  .setDuration(400)
                  .setInterpolator(AccelerateDecelerateInterpolator())
                  .withEndAction {
                    // Start hint arrows after handle pulse completes
                    slideHandle.startHintAnimation()
                  }
                  .start()
              }
              .start()
          }
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
  
  // Track input field position relative to this view for contextual hints
  private var isInputFieldBelow = true // Default assumption

  private var startY = 0f
  private var isDragging = false
  private var currentOffset = 0f
  private val sliderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.argb(220, 255, 165, 0) // Orange handle color
    style = Paint.Style.FILL
  }
  
  // Hint arrows animation
  private var showHints = true
  private var hintAnimationProgress = 0f
  
  private val acceptTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.argb(220, 76, 175, 80) // Green for Accept
    style = Paint.Style.FILL
    textSize = 12.dpToPx().toFloat()
    textAlign = Paint.Align.CENTER
    isFakeBoldText = true // Make text bold for better visibility
  }
  
  private val dismissTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.argb(220, 244, 67, 54) // Red for Dismiss  
    style = Paint.Style.FILL
    textSize = 12.dpToPx().toFloat()
    textAlign = Paint.Align.CENTER
    isFakeBoldText = true // Make text bold for better visibility
  }
  
  private val acceptArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.argb(220, 76, 175, 80) // Green arrows for Accept
    style = Paint.Style.FILL
  }
  
  private val dismissArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.argb(220, 244, 67, 54) // Red arrows for Dismiss
    style = Paint.Style.FILL
  }
  
  
  // Callback to move parent window during slide
  private var onWindowMove: ((Float) -> Unit)? = null
  
  init {
    // Allow drawing hints outside view bounds
    setWillNotDraw(false)
  }
  
  fun setWindowMoveCallback(callback: (Float) -> Unit) {
    onWindowMove = callback
  }
  
  fun setInputFieldPosition(windowY: Int, inputY: Int) {
    isInputFieldBelow = inputY > windowY
    Log.d("BreezeFloatingWindow", "Input field positioning: windowY=$windowY, inputY=$inputY, isBelow=$isInputFieldBelow")
  }
  
  fun startHintAnimation() {
    if (!showHints) return
    
    Log.d("BreezeFloatingWindow", "Starting hint arrow animation")
    
    // 3 bounce cycles over 3 seconds, then fade out
    val animator = ValueAnimator.ofFloat(0f, kotlin.math.PI.toFloat() * 3)
    animator.apply {
      duration = 3000
      interpolator = LinearInterpolator()
      addUpdateListener { animation ->
        hintAnimationProgress = animation.animatedValue as Float
        invalidate()
      }
      addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationStart(animation: android.animation.Animator) {
          Log.d("BreezeFloatingWindow", "Hint animation started")
        }
        override fun onAnimationEnd(animation: android.animation.Animator) {
          Log.d("BreezeFloatingWindow", "Hint animation ended")
          showHints = false
          invalidate()
        }
      })
      start()
    }
  }
  
  fun dismissHints() {
    if (showHints) {
      showHints = false
      clearAnimation()
      invalidate()
    }
  }

  override fun onDraw(canvas: Canvas) {
    // Enhanced pill-shaped handle with grip lines - positioned at bottom
    val handleWidth = 80.dpToPx().toFloat() // Much longer handle for better interaction
    val handleHeight = 12.dpToPx().toFloat() // Much thicker handle
    val handleX = (width - handleWidth) / 2
    val handleY = height - handleHeight - 8.dpToPx() // Balanced position for hint visibility
    
    // Draw subtle background shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.argb(40, 0, 0, 0)
      style = Paint.Style.FILL
    }
    val shadowOffset = 1.dpToPx().toFloat()
    canvas.drawRoundRect(
      handleX, handleY + shadowOffset,
      handleX + handleWidth, handleY + handleHeight + shadowOffset,
      handleHeight / 2, handleHeight / 2, shadowPaint
    )

    // Draw main pill-shaped handle
    canvas.drawRoundRect(
      handleX, handleY,
      handleX + handleWidth, handleY + handleHeight,
      handleHeight / 2, handleHeight / 2,
      sliderPaint
    )
    
    // Draw grip lines for affordance
    val gripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.argb(120, 0, 0, 0) // Semi-transparent dark lines
      style = Paint.Style.STROKE
      strokeWidth = 1.dpToPx().toFloat()
      strokeCap = Paint.Cap.ROUND
    }
    
    val gripY = handleY + handleHeight / 2
    val grip1X = handleX + handleWidth / 2 - 8.dpToPx()
    val grip2X = handleX + handleWidth / 2
    val grip3X = handleX + handleWidth / 2 + 8.dpToPx()
    val gripLength = 3.dpToPx().toFloat()
    
    // Three small grip lines
    canvas.drawLine(grip1X, gripY - gripLength/2, grip1X, gripY + gripLength/2, gripPaint)
    canvas.drawLine(grip2X, gripY - gripLength/2, grip2X, gripY + gripLength/2, gripPaint)
    canvas.drawLine(grip3X, gripY - gripLength/2, grip3X, gripY + gripLength/2, gripPaint)
    
    // Draw animated hint arrows if enabled
    if (showHints) {
      drawHintArrows(canvas, handleX, handleY, handleWidth, handleHeight)
    }
  }
  
  private fun drawHintArrows(canvas: Canvas, handleX: Float, handleY: Float, handleWidth: Float, handleHeight: Float) {
    val bounce = kotlin.math.sin(hintAnimationProgress) * 3.dpToPx() // 3dp bounce
    
    // Keep arrows visible for first 2.5 seconds, then fade out in last 0.5 seconds
    val fadeStartProgress = kotlin.math.PI.toFloat() * 2.5f // Start fading after 2.5 cycles
    val alpha = if (hintAnimationProgress < fadeStartProgress) {
      200 // Full opacity for most of the animation
    } else {
      val fadeProgress = (hintAnimationProgress - fadeStartProgress) / (kotlin.math.PI * 0.5f)
      (200 * (1 - fadeProgress)).toInt().coerceAtLeast(0)
    }
    
    if (alpha <= 0) return // Skip drawing if fully transparent
    
    val arrowSize = 6.dpToPx().toFloat() // Larger arrows for visibility
    // Position arrows above and below handle center, not to the side
    val arrowX = handleX + handleWidth / 2 // Center horizontally with handle
    
    // Up arrow and text (above handle) - with contextual colors
    val upArrowY = handleY - 12.dpToPx() - bounce // Compact spacing above handle
    val isUpAccept = !isInputFieldBelow
    val upArrowPaint = if (isUpAccept) acceptArrowPaint else dismissArrowPaint
    val upTextPaint = if (isUpAccept) acceptTextPaint else dismissTextPaint
    
    upArrowPaint.alpha = alpha
    drawArrow(canvas, arrowX, upArrowY, arrowSize, true, upArrowPaint) // true = pointing up
    
    // Up text hint
    upTextPaint.alpha = alpha
    
    // Contextual text based on input field position
    val upText = if (isInputFieldBelow) "Dismiss" else "Accept"
    val upTextY = upArrowY - 6.dpToPx() // Tight spacing above arrow
    val upTextBounds = Rect()
    upTextPaint.getTextBounds(upText, 0, upText.length, upTextBounds)
    
    // Draw text without background
    canvas.drawText(upText, arrowX, upTextY, upTextPaint)
    
    // Down arrow and text (below handle) - positioned to be visible
    val downArrowY = handleY + handleHeight + 6.dpToPx() + bounce // Good spacing from handle
    val isDownAccept = isInputFieldBelow
    val downArrowPaint = if (isDownAccept) acceptArrowPaint else dismissArrowPaint
    val downTextPaint = if (isDownAccept) acceptTextPaint else dismissTextPaint
    
    downArrowPaint.alpha = alpha
    drawArrow(canvas, arrowX, downArrowY, arrowSize, false, downArrowPaint) // false = pointing down
    
    // Down text hint - ensure it's visible (can extend beyond view bounds)
    downTextPaint.alpha = alpha
    val downText = if (isDownAccept) "Accept" else "Dismiss"
    val downTextY = downArrowY + arrowSize + 12.dpToPx() // Enough space for text visibility
    val downTextBounds = Rect()
    downTextPaint.getTextBounds(downText, 0, downText.length, downTextBounds)
    
    // Draw text without background - will be visible even if extends beyond view
    canvas.drawText(downText, arrowX, downTextY, downTextPaint)
  }
  
  private fun drawArrow(canvas: Canvas, x: Float, y: Float, size: Float, pointingUp: Boolean, paint: Paint) {
    val path = Path().apply {
      if (pointingUp) {
        // Up arrow: ^
        moveTo(x, y)
        lineTo(x - size, y + size)
        lineTo(x + size, y + size)
        close()
      } else {
        // Down arrow: v
        moveTo(x, y + size)
        lineTo(x - size, y)
        lineTo(x + size, y)
        close()
      }
    }
    canvas.drawPath(path, paint)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    Log.d("BreezeFloatingWindow", "SlideHandleView.onTouchEvent: action=${event.action} at (${event.x}, ${event.y})")
    when (event.action) {
      MotionEvent.ACTION_DOWN -> {
        startY = event.y
        isDragging = true
        dismissHints() // Hide hints when user starts interacting
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
