package com.mtkresearch.securesms.breeze.rainbow

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Custom drawable that creates a sparkle effect along the rainbow animation path.
 * Used for brief visual confirmation when messages are sent.
 */
class SparkleEffect(private val context: Context) : Drawable() {
    
    private val sparkleCount = 8
    private val sparkles = mutableListOf<Sparkle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    
    private var progress = 0f
    private var isAnimating = false
    private var sparkleAnimator: ValueAnimator? = null
    
    // Path calculation
    private var borderPath: Path? = null
    private var pathLength = 0f
    
    data class Sparkle(
        var position: PointF = PointF(),
        var size: Float = 0f,
        var alpha: Int = 255,
        var rotation: Float = 0f
    )
    
    init {
        initializeSparkles()
    }
    
    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        calculateBorderPath()
        initializeSparkles()
    }
    
    override fun draw(canvas: Canvas) {
        if (!isAnimating || borderPath == null) return
        
        sparkles.forEach { sparkle ->
            if (sparkle.alpha > 0) {
                canvas.save()
                canvas.translate(sparkle.position.x, sparkle.position.y)
                canvas.rotate(sparkle.rotation)
                
                paint.alpha = sparkle.alpha
                
                // Draw star shape
                drawStar(canvas, sparkle.size)
                
                canvas.restore()
            }
        }
    }
    
    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }
    
    override fun getOpacity(): Int = paint.alpha
    
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }
    
    /**
     * Starts the sparkle effect animation
     */
    fun startSparkleAnimation() {
        if (isAnimating) return
        
        isAnimating = true
        initializeSparkles()
        
        sparkleAnimator?.cancel()
        sparkleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500 // 0.5 seconds
            interpolator = DecelerateInterpolator()
            
            addUpdateListener { animation ->
                progress = animation.animatedValue as Float
                updateSparkles()
                invalidateSelf()
            }
            
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isAnimating = false
                    progress = 0f
                    invalidateSelf()
                }
            })
            
            start()
        }
    }
    
    /**
     * Stops the sparkle animation
     */
    fun stopSparkleAnimation() {
        sparkleAnimator?.cancel()
        isAnimating = false
        progress = 0f
        invalidateSelf()
    }
    
    /**
     * Checks if sparkle animation is currently running
     */
    fun isSparkleAnimating(): Boolean = isAnimating
    
    private fun calculateBorderPath() {
        val bounds = bounds
        if (bounds.isEmpty) return
        
        val strokeWidth = convertDpToPixels(AnimationConfig.BORDER_WIDTH_DP)
        val halfStroke = strokeWidth / 2f
        val cornerRadius = minOf(bounds.width(), bounds.height()) * 0.1f
        
        borderPath = Path().apply {
            addRoundRect(
                bounds.left + halfStroke,
                bounds.top + halfStroke,
                bounds.right - halfStroke,
                bounds.bottom - halfStroke,
                cornerRadius,
                cornerRadius,
                Path.Direction.CW
            )
        }
        
        // Approximate path length for positioning sparkles
        pathLength = 2f * (bounds.width() + bounds.height() - 4 * halfStroke)
    }
    
    private fun initializeSparkles() {
        sparkles.clear()
        repeat(sparkleCount) { index ->
            sparkles.add(Sparkle().apply {
                // Distribute sparkles evenly along the path
                val pathProgress = (index.toFloat() / sparkleCount)
                position = getPositionOnPath(pathProgress)
                size = convertDpToPixels(3f + Random.nextFloat() * 2f) // 3-5dp
                alpha = 0
                rotation = Random.nextFloat() * 360f
            })
        }
    }
    
    private fun updateSparkles() {
        sparkles.forEachIndexed { index, sparkle ->
            // Calculate sparkle progress based on overall progress and its position
            val sparkleDelay = (index.toFloat() / sparkleCount) * 0.3f // Stagger appearance
            val sparkleProgress = ((progress - sparkleDelay) / 0.7f).coerceIn(0f, 1f)
            
            if (sparkleProgress > 0f) {
                // Update position along path
                val pathProgress = ((index.toFloat() / sparkleCount) + progress * 0.2f) % 1f
                sparkle.position = getPositionOnPath(pathProgress)
                
                // Animate size and alpha
                val fadeIn = (sparkleProgress * 3f).coerceAtMost(1f)
                val fadeOut = ((1f - sparkleProgress) * 3f).coerceAtMost(1f)
                val visibility = minOf(fadeIn, fadeOut)
                
                sparkle.alpha = (255 * visibility).toInt()
                sparkle.size = convertDpToPixels(2f + 3f * visibility)
                sparkle.rotation += 10f // Rotate sparkles
            } else {
                sparkle.alpha = 0
            }
        }
    }
    
    private fun getPositionOnPath(progress: Float): PointF {
        val bounds = bounds
        val strokeWidth = convertDpToPixels(AnimationConfig.BORDER_WIDTH_DP)
        val halfStroke = strokeWidth / 2f
        
        val rect = android.graphics.RectF(
            bounds.left + halfStroke,
            bounds.top + halfStroke,
            bounds.right - halfStroke,
            bounds.bottom - halfStroke
        )
        
        val perimeter = 2f * (rect.width() + rect.height())
        val distance = progress * perimeter
        
        return when {
            distance <= rect.width() -> {
                // Top edge
                PointF(rect.left + distance, rect.top)
            }
            distance <= rect.width() + rect.height() -> {
                // Right edge
                PointF(rect.right, rect.top + (distance - rect.width()))
            }
            distance <= 2 * rect.width() + rect.height() -> {
                // Bottom edge
                PointF(rect.right - (distance - rect.width() - rect.height()), rect.bottom)
            }
            else -> {
                // Left edge
                PointF(rect.left, rect.bottom - (distance - 2 * rect.width() - rect.height()))
            }
        }
    }
    
    private fun drawStar(canvas: Canvas, size: Float) {
        val radius = size / 2f
        val innerRadius = radius * 0.4f
        val path = Path()
        
        for (i in 0 until 10) {
            val angle = (i * 36 - 90) * Math.PI / 180
            val r = if (i % 2 == 0) radius else innerRadius
            val x = (cos(angle) * r).toFloat()
            val y = (sin(angle) * r).toFloat()
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        path.close()
        canvas.drawPath(path, paint)
    }
    
    private fun convertDpToPixels(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}