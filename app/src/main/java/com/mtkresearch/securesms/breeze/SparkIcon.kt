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
 * Simplified spark icon following enhanced spec:
 * - Size: 18-22dp (spec compliant)
 * - Color: white @ 80% (spec compliant)
 * - Animation: soft twinkle 300-500ms
 * - Clean, minimal implementation
 */
class SparkIcon private constructor(
  private val context: Context,
  private val anchorBounds: Rect,
  private val onTapped: () -> Unit
) {

  companion object {
    fun create(context: Context, anchorBounds: Rect, onTapped: () -> Unit): SparkIcon {
      return SparkIcon(context, anchorBounds, onTapped)
    }

    private const val ICON_SIZE_DP = 48 // Increased for better visibility
    private const val TWINKLE_DURATION_MS = 400L // 300-500ms per spec
  }

  private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  private var iconView: SparkIconView? = null
  private var layoutParams: WindowManager.LayoutParams? = null

  fun show() {
    try {
      remove() // Ensure clean state

      Log.d("SparkIcon", "Creating spark icon with bounds: $anchorBounds")

      iconView = SparkIconView(context, onTapped)
      layoutParams = createLayoutParams()

      Log.d("SparkIcon", "Adding spark icon to window manager at position (${layoutParams?.x}, ${layoutParams?.y})")
      windowManager.addView(iconView, layoutParams)
      iconView?.startTwinkle()

      Log.d("SparkIcon", "Spark icon shown successfully")
    } catch (e: Exception) {
      Log.e("SparkIcon", "Failed to show spark icon", e)
    }
  }

  fun remove() {
    iconView?.let { view ->
      view.stopTwinkle()
      if (view.isAttachedToWindow) {
        windowManager.removeView(view)
      }
    }
    iconView = null
    layoutParams = null
  }

  private fun createLayoutParams(): WindowManager.LayoutParams {
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

    // Position above the input field to avoid overlap with buttons
    // Per enhanced spec: placement near input field but visible
    params.x = anchorBounds.left + 16.dpToPx()
    params.y = anchorBounds.top - size - 16.dpToPx()

    // Ensure it stays on screen
    val displayMetrics = context.resources.displayMetrics
    params.x = kotlin.math.max(16.dpToPx(), kotlin.math.min(params.x, displayMetrics.widthPixels - size - 16.dpToPx()))
    params.y = kotlin.math.max(50.dpToPx(), params.y) // Keep it below status bar

    Log.d("SparkIcon", "Calculated position: x=${params.x}, y=${params.y}, size=$size, anchorBounds=$anchorBounds")

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
 * Simple spark icon view with spec-compliant styling.
 */
private class SparkIconView(
  context: Context,
  private val onTapped: () -> Unit
) : View(context) {

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor("#FFA500") // Bright orange for maximum visibility
    style = Paint.Style.FILL
  }

  private var twinkleAnimator: ObjectAnimator? = null
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
    val baseRadius = min(width, height) / 6f

    // Animated radius with soft twinkle
    val animatedRadius = baseRadius * (0.8f + 0.2f * sin(animationProgress * 2 * PI.toFloat()))

    // Draw simple 4-pointed star (spark shape)
    val path = Path().apply {
      val outerRadius = animatedRadius * 1.2f
      val innerRadius = animatedRadius * 0.5f

      // Top point
      moveTo(centerX, centerY - outerRadius)
      lineTo(centerX + innerRadius * 0.3f, centerY - innerRadius * 0.3f)

      // Right point
      lineTo(centerX + outerRadius, centerY)
      lineTo(centerX + innerRadius * 0.3f, centerY + innerRadius * 0.3f)

      // Bottom point
      lineTo(centerX, centerY + outerRadius)
      lineTo(centerX - innerRadius * 0.3f, centerY + innerRadius * 0.3f)

      // Left point
      lineTo(centerX - outerRadius, centerY)
      lineTo(centerX - innerRadius * 0.3f, centerY - innerRadius * 0.3f)

      close()
    }

    canvas.drawPath(path, paint)
  }

  fun startTwinkle() {
    stopTwinkle()

    twinkleAnimator = ObjectAnimator.ofFloat(this, "animationProgress", 0f, 2f * PI.toFloat()).apply {
      duration = 400L // 300-500ms per spec
      repeatCount = ObjectAnimator.INFINITE
      repeatMode = ObjectAnimator.RESTART
      start()
    }
  }

  fun stopTwinkle() {
    twinkleAnimator?.cancel()
    twinkleAnimator = null
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    stopTwinkle()
  }
}
