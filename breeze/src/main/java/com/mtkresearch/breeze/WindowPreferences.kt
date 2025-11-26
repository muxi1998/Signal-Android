package com.mtkresearch.breeze

import android.content.Context
import android.content.SharedPreferences
import org.signal.core.util.logging.Log

/**
 * Manages user preferences for floating window size and position.
 *
 * Stores and retrieves window dimensions and position to maintain
 * user customizations across sessions.
 */
class WindowPreferences(context: Context) {

  companion object {
    private val TAG = Log.tag(WindowPreferences::class.java)
    private const val PREFS_NAME = "breeze_window_preferences"

    // Preference keys
    private const val KEY_WINDOW_WIDTH = "window_width"
    private const val KEY_WINDOW_HEIGHT = "window_height"
    private const val KEY_WINDOW_X = "window_x"
    private const val KEY_WINDOW_Y = "window_y"
    private const val KEY_HAS_SAVED_POSITION = "has_saved_position"

    // Default values
    const val DEFAULT_WIDTH_DP = 300
    const val DEFAULT_HEIGHT_DP = 150
    const val MIN_WIDTH_DP = 250
    const val MIN_HEIGHT_DP = 120
    const val MAX_WIDTH_DP = 450
    const val MAX_HEIGHT_DP = 300
  }

  private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val displayMetrics = context.resources.displayMetrics

  /**
   * Data class for window dimensions and position.
   */
  data class WindowSettings(
    val width: Int,
    val height: Int,
    val x: Int,
    val y: Int,
    val hasSavedPosition: Boolean
  )

  /**
   * Get saved window settings or defaults.
   */
  fun getWindowSettings(): WindowSettings {
    val width = sharedPrefs.getInt(KEY_WINDOW_WIDTH, DEFAULT_WIDTH_DP.dpToPx())
    val height = sharedPrefs.getInt(KEY_WINDOW_HEIGHT, DEFAULT_HEIGHT_DP.dpToPx())
    val x = sharedPrefs.getInt(KEY_WINDOW_X, -1)
    val y = sharedPrefs.getInt(KEY_WINDOW_Y, -1)
    val hasSavedPosition = sharedPrefs.getBoolean(KEY_HAS_SAVED_POSITION, false)

    Log.d(TAG, "Retrieved window settings: ${width}x$height at ($x, $y), hasSaved=$hasSavedPosition")

    return WindowSettings(
      width = constrainWidth(width),
      height = constrainHeight(height),
      x = if (hasSavedPosition) constrainX(x, width) else -1,
      y = if (hasSavedPosition) constrainY(y, height) else -1,
      hasSavedPosition = hasSavedPosition
    )
  }

  /**
   * Save window size.
   */
  fun saveWindowSize(width: Int, height: Int) {
    val constrainedWidth = constrainWidth(width)
    val constrainedHeight = constrainHeight(height)

    sharedPrefs.edit()
      .putInt(KEY_WINDOW_WIDTH, constrainedWidth)
      .putInt(KEY_WINDOW_HEIGHT, constrainedHeight)
      .apply()

    Log.d(TAG, "Saved window size: ${constrainedWidth}x$constrainedHeight")
  }

  /**
   * Save window position.
   */
  fun saveWindowPosition(x: Int, y: Int, width: Int, height: Int) {
    val constrainedX = constrainX(x, width)
    val constrainedY = constrainY(y, height)

    sharedPrefs.edit()
      .putInt(KEY_WINDOW_X, constrainedX)
      .putInt(KEY_WINDOW_Y, constrainedY)
      .putBoolean(KEY_HAS_SAVED_POSITION, true)
      .apply()

    Log.d(TAG, "Saved window position: ($constrainedX, $constrainedY)")
  }

  /**
   * Save both size and position.
   */
  fun saveWindowSettings(width: Int, height: Int, x: Int, y: Int) {
    val constrainedWidth = constrainWidth(width)
    val constrainedHeight = constrainHeight(height)
    val constrainedX = constrainX(x, constrainedWidth)
    val constrainedY = constrainY(y, constrainedHeight)

    sharedPrefs.edit()
      .putInt(KEY_WINDOW_WIDTH, constrainedWidth)
      .putInt(KEY_WINDOW_HEIGHT, constrainedHeight)
      .putInt(KEY_WINDOW_X, constrainedX)
      .putInt(KEY_WINDOW_Y, constrainedY)
      .putBoolean(KEY_HAS_SAVED_POSITION, true)
      .apply()

    Log.d(TAG, "Saved window settings: ${constrainedWidth}x$constrainedHeight at ($constrainedX, $constrainedY)")
  }

  /**
   * Clear all saved preferences.
   */
  fun clearPreferences() {
    sharedPrefs.edit().clear().apply()
    Log.d(TAG, "Cleared all window preferences")
  }

  private fun constrainWidth(width: Int): Int {
    val minWidth = MIN_WIDTH_DP.dpToPx()
    val maxWidth = kotlin.math.min(MAX_WIDTH_DP.dpToPx(), displayMetrics.widthPixels - 32.dpToPx())
    return kotlin.math.max(minWidth, kotlin.math.min(width, maxWidth))
  }

  private fun constrainHeight(height: Int): Int {
    val minHeight = MIN_HEIGHT_DP.dpToPx()
    val maxHeight = kotlin.math.min(MAX_HEIGHT_DP.dpToPx(), displayMetrics.heightPixels - 100.dpToPx())
    return kotlin.math.max(minHeight, kotlin.math.min(height, maxHeight))
  }

  private fun constrainX(x: Int, width: Int): Int {
    val margin = 16.dpToPx()
    return kotlin.math.max(margin, kotlin.math.min(x, displayMetrics.widthPixels - width - margin))
  }

  private fun constrainY(y: Int, height: Int): Int {
    val margin = 16.dpToPx()
    return kotlin.math.max(margin, kotlin.math.min(y, displayMetrics.heightPixels - height - margin))
  }

  private fun Int.dpToPx(): Int {
    return (this * displayMetrics.density).toInt()
  }
}
