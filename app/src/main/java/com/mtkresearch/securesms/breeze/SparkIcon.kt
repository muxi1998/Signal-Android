package com.mtkresearch.securesms.breeze

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import org.signal.core.util.logging.Log
import kotlin.math.*

/**
 * Magic wand icon following enhanced spec:
 * - Size: 18-22dp (spec compliant)
 * - Color: orange with sparkles (more intuitive for AI)
 * - Animation: gentle sparkle effect
 * - Magic wand design for clear AI transformation intent
 */
class SparkIcon private constructor(
  private val context: Context,
  private val anchorView: View?, // Store the actual view for dynamic positioning
  private val fallbackBounds: Rect, // Fallback if view is null
  private val onTapped: () -> Unit
) {

  companion object {
    fun create(context: Context, anchorView: View?, fallbackBounds: Rect, onTapped: () -> Unit): SparkIcon {
      return SparkIcon(context, anchorView, fallbackBounds, onTapped)
    }
    
    // For backward compatibility with existing callers
    fun create(context: Context, anchorBounds: Rect, onTapped: () -> Unit): SparkIcon {
      return SparkIcon(context, null, anchorBounds, onTapped)
    }

    private const val ICON_SIZE_DP = 48 // Increased for better visibility
    private const val TWINKLE_DURATION_MS = 400L // 300-500ms per spec
  }

  private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  private var iconView: SparkIconView? = null
  private var layoutParams: WindowManager.LayoutParams? = null
  
  // Dynamic positioning support
  private var layoutChangeListener: View.OnLayoutChangeListener? = null
  private var isAnimatingPosition = false

  fun show() {
    try {
      remove() // Ensure clean state

      val currentBounds = getCurrentBounds()
      Log.d("SparkIcon", "Creating magic wand with bounds: $currentBounds")

      iconView = SparkIconView(context, onTapped)
      layoutParams = createLayoutParams(currentBounds)

      Log.d("SparkIcon", "Adding magic wand to window manager at position (${layoutParams?.x}, ${layoutParams?.y})")
      windowManager.addView(iconView, layoutParams)
      iconView?.startSparkle()
      
      // Setup dynamic positioning if we have an anchor view
      setupDynamicPositioning()

      Log.d("SparkIcon", "Magic wand shown successfully with dynamic positioning")
    } catch (e: Exception) {
      Log.e("SparkIcon", "Failed to show magic wand", e)
    }
  }

  fun remove() {
    // Clean up layout change listener first
    layoutChangeListener?.let { listener ->
      anchorView?.removeOnLayoutChangeListener(listener)
    }
    layoutChangeListener = null
    
    iconView?.let { view ->
      view.stopSparkle()
      if (view.isAttachedToWindow) {
        windowManager.removeView(view)
      }
    }
    iconView = null
    layoutParams = null
  }

  private fun getCurrentBounds(): Rect {
    return if (anchorView != null && anchorView.isAttachedToWindow) {
      // Get real-time bounds from the anchor view
      val location = IntArray(2)
      anchorView.getLocationOnScreen(location)
      Rect(location[0], location[1], location[0] + anchorView.width, location[1] + anchorView.height)
    } else {
      // Use fallback bounds if view is not available
      fallbackBounds
    }
  }
  
  private fun setupDynamicPositioning() {
    anchorView?.let { view ->
      layoutChangeListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        // Only update if bounds actually changed to avoid unnecessary work
        if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
          Log.d("SparkIcon", "Anchor view layout changed, updating wand position")
          updateIconPosition()
        }
      }
      view.addOnLayoutChangeListener(layoutChangeListener)
      Log.d("SparkIcon", "Dynamic positioning listener attached")
    }
  }
  
  private fun updateIconPosition() {
    if (isAnimatingPosition) return // Avoid conflicting animations
    
    layoutParams?.let { params ->
      val newBounds = getCurrentBounds()
      val newPosition = calculateOptimalPosition(newBounds)
      
      // Check if position actually changed to avoid unnecessary updates
      if (params.x == newPosition.x && params.y == newPosition.y) {
        Log.d("SparkIcon", "Position unchanged, skipping update")
        return
      }
      
      iconView?.let { view ->
        if (view.isAttachedToWindow) {
          animateToPosition(params.x, params.y, newPosition.x, newPosition.y)
        }
      }
    }
  }
  
  private fun animateToPosition(fromX: Int, fromY: Int, toX: Int, toY: Int) {
    isAnimatingPosition = true
    
    val startTime = System.currentTimeMillis()
    val duration = 250L // Smooth but not too slow
    
    val animationHandler = android.os.Handler(android.os.Looper.getMainLooper())
    
    fun updateFrame() {
      val elapsed = System.currentTimeMillis() - startTime
      val progress = kotlin.math.min(1f, elapsed.toFloat() / duration)
      
      // Use smooth easing (ease-out)
      val easedProgress = 1f - (1f - progress) * (1f - progress)
      
      val currentX = (fromX + (toX - fromX) * easedProgress).toInt()
      val currentY = (fromY + (toY - fromY) * easedProgress).toInt()
      
      layoutParams?.let { params ->
        params.x = currentX
        params.y = currentY
        
        iconView?.let { view ->
          if (view.isAttachedToWindow) {
            windowManager.updateViewLayout(view, params)
          }
        }
      }
      
      if (progress < 1f) {
        animationHandler.postDelayed(::updateFrame, 16) // ~60 FPS
      } else {
        isAnimatingPosition = false
        Log.d("SparkIcon", "Magic wand smoothly repositioned to ($toX, $toY)")
      }
    }
    
    updateFrame()
  }

  private fun createLayoutParams(anchorBounds: Rect): WindowManager.LayoutParams {
    val size = ICON_SIZE_DP.dpToPx()
    val params = WindowManager.LayoutParams(
      size,
      size,
      getOverlayType(),
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
      PixelFormat.TRANSLUCENT
    )

    params.gravity = Gravity.TOP or Gravity.START

    // Use the new optimal positioning logic
    val optimalPosition = calculateOptimalPosition(anchorBounds)
    params.x = optimalPosition.x
    params.y = optimalPosition.y

    Log.d("SparkIcon", "Calculated optimal position: x=${params.x}, y=${params.y}, size=$size, anchorBounds=$anchorBounds")

    return params
  }
  
  private fun calculateOptimalPosition(anchorBounds: Rect): android.graphics.Point {
    val size = ICON_SIZE_DP.dpToPx()
    val displayMetrics = context.resources.displayMetrics
    val screenBounds = Rect(0, 50.dpToPx(), displayMetrics.widthPixels, displayMetrics.heightPixels)
    val keyboardBounds = getKeyboardBounds()
    
    // Define candidate positions with preference for LEFT side (user request)
    val candidates = generateCandidatePositions(anchorBounds, size)
    
    var bestPosition: PositionCandidate? = null
    var bestScore = -1
    
    for (candidate in candidates) {
      val score = calculatePositionScore(candidate, anchorBounds, screenBounds, keyboardBounds, size)
      Log.d("SparkIcon", "Position ${candidate.type}: (${candidate.x}, ${candidate.y}) scored $score")
      
      if (score > bestScore) {
        bestScore = score
        bestPosition = candidate
      }
    }
    
    val finalPosition = bestPosition ?: candidates.first() // Fallback to first if all fail
    Log.d("SparkIcon", "Selected position: ${finalPosition.type} (${finalPosition.x}, ${finalPosition.y}) with score $bestScore")
    
    return android.graphics.Point(finalPosition.x, finalPosition.y)
  }
  
  private fun generateCandidatePositions(anchorBounds: Rect, size: Int): List<PositionCandidate> {
    val margin = 16.dpToPx()
    val candidates = mutableListOf<PositionCandidate>()
    
    // LEFT side positions (PRIMARY preference per user request)
    candidates.add(PositionCandidate(
      type = "LEFT_CENTER",
      x = anchorBounds.left - size - margin,
      y = anchorBounds.centerY() - size / 2,
      preferenceScore = 30 // Highest preference
    ))
    
    candidates.add(PositionCandidate(
      type = "LEFT_TOP", 
      x = anchorBounds.left - size - margin,
      y = anchorBounds.top,
      preferenceScore = 25
    ))
    
    candidates.add(PositionCandidate(
      type = "LEFT_BOTTOM",
      x = anchorBounds.left - size - margin, 
      y = anchorBounds.bottom - size,
      preferenceScore = 20
    ))
    
    // ABOVE positions (secondary options)
    candidates.add(PositionCandidate(
      type = "ABOVE_LEFT",
      x = anchorBounds.left,
      y = anchorBounds.top - size - margin,
      preferenceScore = 15
    ))
    
    candidates.add(PositionCandidate(
      type = "ABOVE_RIGHT", 
      x = anchorBounds.right - size,
      y = anchorBounds.top - size - margin,
      preferenceScore = 10
    ))
    
    // RIGHT side (backup if left doesn't work)
    candidates.add(PositionCandidate(
      type = "RIGHT_CENTER",
      x = anchorBounds.right + margin,
      y = anchorBounds.centerY() - size / 2,
      preferenceScore = 5
    ))
    
    // BELOW positions (last resort)
    candidates.add(PositionCandidate(
      type = "BELOW_LEFT",
      x = anchorBounds.left, 
      y = anchorBounds.bottom + margin,
      preferenceScore = 2
    ))
    
    return candidates
  }
  
  private fun calculatePositionScore(
    candidate: PositionCandidate, 
    anchorBounds: Rect, 
    screenBounds: Rect, 
    keyboardBounds: Rect?,
    size: Int
  ): Int {
    val candidateRect = Rect(candidate.x, candidate.y, candidate.x + size, candidate.y + size)
    var score = 100 + candidate.preferenceScore
    
    // DISQUALIFYING CONDITIONS (return negative score)
    
    // 1. Off screen?
    if (!screenBounds.contains(candidateRect)) {
      Log.d("SparkIcon", "${candidate.type}: OFF SCREEN - disqualified")
      return -100
    }
    
    // 2. Overlaps with keyboard?
    if (keyboardBounds != null && Rect.intersects(candidateRect, keyboardBounds)) {
      Log.d("SparkIcon", "${candidate.type}: KEYBOARD OVERLAP - disqualified") 
      return -90
    }
    
    // 3. Overlaps with input field?
    if (Rect.intersects(candidateRect, anchorBounds)) {
      Log.d("SparkIcon", "${candidate.type}: INPUT OVERLAP - disqualified")
      return -80
    }
    
    // SCORING FACTORS (adjust score based on quality)
    
    // 4. Distance from input field (closer is better, but not too close)
    val centerDistance = calculateDistance(candidateRect, anchorBounds)
    val idealDistance = size.toFloat() * 1.5f // Ideal spacing
    val distancePenalty = kotlin.math.abs(centerDistance - idealDistance).toInt() / 10
    score -= distancePenalty
    
    // 5. Screen edge proximity (avoid being too close to edges)
    val edgeMargin = 32.dpToPx()
    if (candidate.x < edgeMargin) score -= 10 // Too close to left edge
    if (candidate.y < edgeMargin) score -= 10 // Too close to top edge
    if (candidate.x + size > screenBounds.right - edgeMargin) score -= 10 // Too close to right edge
    if (candidate.y + size > screenBounds.bottom - edgeMargin) score -= 10 // Too close to bottom edge
    
    return score
  }
  
  private fun calculateDistance(rect1: Rect, rect2: Rect): Float {
    val dx = (rect1.centerX() - rect2.centerX()).toFloat()
    val dy = (rect1.centerY() - rect2.centerY()).toFloat()
    return kotlin.math.sqrt(dx * dx + dy * dy)
  }
  
  private fun getKeyboardBounds(): Rect? {
    // Simplified keyboard detection - in real implementation, this would use WindowInsets
    // For now, return null (no keyboard bounds available)
    return null
  }
  
  private data class PositionCandidate(
    val type: String,
    val x: Int,
    val y: Int, 
    val preferenceScore: Int
  )

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
 * Magic wand icon view with sparkle animation.
 */
private class SparkIconView(
  context: Context,
  private val onTapped: () -> Unit
) : View(context) {

  private val wandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor("#8B4513") // Brown handle
    style = Paint.Style.FILL
    strokeCap = Paint.Cap.ROUND
  }
  
  private val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor("#FFD700") // Golden tip
    style = Paint.Style.FILL
  }
  
  private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor("#FFA500") // Orange sparkles
    style = Paint.Style.FILL
  }

  private var sparkleAnimator: ObjectAnimator? = null
  private var animationProgress = 0f
    set(value) {
      field = value
      invalidate()
    }

  init {
    setOnClickListener { onTapped() }
  }

  override fun onDraw(canvas: Canvas) {
    val centerX = width / 2f
    val centerY = height / 2f
    val size = min(width, height) * 0.8f
    
    // Magic wand dimensions
    val wandLength = size * 0.7f
    val wandThickness = size * 0.08f
    val tipSize = size * 0.25f
    
    // Wand positioned diagonally for dynamic look
    val angle = -30f * PI.toFloat() / 180f
    val wandStartX = centerX - wandLength * cos(angle) / 2
    val wandStartY = centerY + wandLength * sin(angle) / 2
    val wandEndX = centerX + wandLength * cos(angle) / 2
    val wandEndY = centerY - wandLength * sin(angle) / 2
    
    // Draw wand handle (brown stick)
    wandPaint.strokeWidth = wandThickness
    wandPaint.style = Paint.Style.STROKE
    canvas.drawLine(wandStartX, wandStartY, wandEndX, wandEndY, wandPaint)
    
    // Draw golden star tip
    drawStar(canvas, wandEndX, wandEndY, tipSize, tipPaint)
    
    // Draw animated sparkles around the tip with varied timing
    val sparkle1Offset = sin(animationProgress) * tipSize * 0.4f
    val sparkle2Offset = sin(animationProgress * 1.5f) * tipSize * 0.3f
    val sparkle3Offset = sin(animationProgress * 0.8f) * tipSize * 0.35f
    
    // Pulsating sparkle alpha for magical effect
    val sparkleAlpha1 = (100 + 155 * abs(sin(animationProgress * 1.2f))).toInt()
    val sparkleAlpha2 = (80 + 175 * abs(sin(animationProgress * 1.8f))).toInt()
    val sparkleAlpha3 = (120 + 135 * abs(sin(animationProgress * 0.9f))).toInt()
    
    // Multiple sparkles with individual animations
    val sparkleData = listOf(
      Triple(wandEndX + tipSize * 0.9f + sparkle1Offset, wandEndY - tipSize * 0.4f, sparkleAlpha1),
      Triple(wandEndX + tipSize * 0.6f, wandEndY - tipSize * 0.9f + sparkle2Offset, sparkleAlpha2),
      Triple(wandEndX - tipSize * 0.4f + sparkle3Offset, wandEndY - tipSize * 0.6f, sparkleAlpha3)
    )
    
    sparkleData.forEach { (x, y, alpha) ->
      sparklePaint.alpha = alpha
      drawStar(canvas, x, y, tipSize * 0.25f, sparklePaint)
    }
  }
  
  private fun drawStar(canvas: Canvas, centerX: Float, centerY: Float, size: Float, paint: Paint) {
    val path = Path().apply {
      val outerRadius = size / 2
      val innerRadius = size / 4
      
      for (i in 0 until 5) {
        val outerAngle = (i * 72 - 90) * PI.toFloat() / 180
        val innerAngle = ((i + 0.5f) * 72 - 90) * PI.toFloat() / 180
        
        val outerX = centerX + cos(outerAngle) * outerRadius
        val outerY = centerY + sin(outerAngle) * outerRadius
        val innerX = centerX + cos(innerAngle) * innerRadius  
        val innerY = centerY + sin(innerAngle) * innerRadius
        
        if (i == 0) {
          moveTo(outerX, outerY)
        } else {
          lineTo(outerX, outerY)
        }
        lineTo(innerX, innerY)
      }
      close()
    }
    canvas.drawPath(path, paint)
  }

  fun startSparkle() {
    stopSparkle()

    sparkleAnimator = ObjectAnimator.ofFloat(this, "animationProgress", 0f, 2f * PI.toFloat()).apply {
      duration = 1000L // Slower, more magical sparkle effect
      repeatCount = ObjectAnimator.INFINITE
      repeatMode = ObjectAnimator.RESTART
      start()
    }
  }

  fun stopSparkle() {
    sparkleAnimator?.cancel()
    sparkleAnimator = null
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    stopSparkle()
  }
}
