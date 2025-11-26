package com.mtkresearch.securesms.breeze.rainbow

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils

/**
 * Custom drawable that renders an animated rainbow gradient border around a view.
 * Uses SweepGradient with matrix rotation for hardware-accelerated performance.
 */
class RainbowGradientDrawable(private val context: Context) : Drawable() {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = convertDpToPixels(AnimationConfig.BORDER_WIDTH_DP)
    }
    
    private val shaderMatrix = Matrix()
    private var sweepShader: SweepGradient? = null
    private var currentRotation = 0f
    private var centerX = 0f
    private var centerY = 0f
    
    // Animation state
    private var animationState = AnimationState()
    private var currentAnimator: ValueAnimator? = null
    
    // Colors for different states
    private var idleColors = ColorConfig.ROYGBIV_SPECTRUM.clone()
    private var typingColors = generateBrighterColors(ColorConfig.ROYGBIV_SPECTRUM)
    
    init {
        updateShaderColors()
    }
    
    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        centerX = bounds.exactCenterX()
        centerY = bounds.exactCenterY()
        createSweepShader()
    }
    
    override fun draw(canvas: Canvas) {
        sweepShader?.let { shader ->
            // Hardware-accelerated matrix rotation
            shaderMatrix.setRotate(currentRotation, centerX, centerY)
            shader.setLocalMatrix(shaderMatrix)
            
            // Draw rainbow border with rounded corners
            val strokeWidth = paint.strokeWidth
            val halfStroke = strokeWidth / 2f
            val rect = RectF(
                bounds.left + halfStroke,
                bounds.top + halfStroke,
                bounds.right - halfStroke,
                bounds.bottom - halfStroke
            )
            
            // Use compose bubble corner radius (20dp)
            val cornerRadius = convertDpToPixels(20f)
            
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
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
     * Updates the animation with new rotation and state
     */
    fun updateAnimation(rotation: Float, state: AnimationStateType = AnimationStateType.IDLE) {
        if (state != animationState.type) {
            animationState = when (state) {
                AnimationStateType.TYPING -> animationState.toTyping()
                AnimationStateType.IDLE -> animationState.toIdle()
            }
            updateShaderColors()
        }
        
        currentRotation = rotation % 360f
        invalidateSelf()
    }
    
    /**
     * Gets the current animation rotation
     */
    fun getCurrentRotation(): Float = currentRotation
    
    /**
     * Gets the current animation state
     */
    fun isTypingMode(): Boolean = animationState.type == AnimationStateType.TYPING
    
    /**
     * Sets the border width for the rainbow animation
     */
    fun setBorderWidth(widthPx: Float) {
        paint.strokeWidth = widthPx
        invalidateSelf()
    }
    
    /**
     * Gets the current border width
     */
    fun getBorderWidth(): Float = paint.strokeWidth
    
    /**
     * Updates colors for theme changes
     */
    fun updateForTheme(isDarkTheme: Boolean) {
        // Rainbow colors work well in both themes, but we can adjust opacity
        val alpha = if (isDarkTheme) 255 else 230 // Slightly transparent in light theme
        paint.alpha = alpha
        invalidateSelf()
    }
    
    private fun createSweepShader() {
        val colors = if (animationState.type == AnimationStateType.TYPING) typingColors else idleColors
        sweepShader = SweepGradient(centerX, centerY, colors, ColorConfig.COLOR_POSITIONS)
        paint.shader = sweepShader
    }
    
    private fun updateShaderColors() {
        createSweepShader()
        invalidateSelf()
    }
    
    private fun generateBrighterColors(baseColors: IntArray): IntArray {
        return baseColors.map { color ->
            ColorUtils.blendARGB(color, Color.WHITE, ColorConfig.TYPING_BRIGHTNESS_BLEND)
        }.toIntArray()
    }
    
    private fun convertDpToPixels(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}