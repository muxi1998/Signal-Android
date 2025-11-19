package com.mtkresearch.securesms.breeze

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.signal.core.util.logging.Log

/**
 * Manages overlay permissions required for the Breeze AI Floating Assistant.
 *
 * Handles checking for SYSTEM_ALERT_WINDOW permission and guiding users
 * through the permission request process on different Android versions.
 */
class PermissionManager(private val context: Context) {

  companion object {
    private val TAG = Log.tag(PermissionManager::class.java)
  }

  fun hasRequiredPermissions(): Boolean {
    return hasSystemAlertWindowPermission()
  }

  suspend fun requestPermissions(): Boolean {
    if (hasRequiredPermissions()) {
      return true
    }

    return requestSystemAlertWindowPermission()
  }

  /**
   * Check if the app has overlay permission (SYSTEM_ALERT_WINDOW).
   */
  private fun hasSystemAlertWindowPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      Settings.canDrawOverlays(context)
    } else {
      // Pre-Android 6.0 doesn't require runtime permission
      true
    }
  }

  /**
   * Request overlay permission by directing user to system settings.
   *
   * @return true if permission was already granted, false if user needs to grant it
   */
  private fun requestSystemAlertWindowPermission(): Boolean {
    if (hasSystemAlertWindowPermission()) {
      return true
    }

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(
          Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
          Uri.parse("package:${context.packageName}")
        ).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        context.startActivity(intent)
        Log.i(TAG, "Directed user to overlay permission settings")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to open overlay permission settings", e)
    }

    return false
  }

  /**
   * Get a user-friendly explanation for why overlay permission is needed.
   */
  fun getPermissionRationale(): String {
    return "Breeze AI Assistant needs overlay permission to show helpful text suggestions " +
      "while you're typing in other apps. This allows the AI to provide assistance " +
      "without interrupting your workflow."
  }

  /**
   * Check if we should show permission rationale to the user.
   * For overlay permission, we always show rationale if permission is missing.
   */
  fun shouldShowPermissionRationale(): Boolean {
    return !hasRequiredPermissions()
  }

  /**
   * Get instructions for manual permission grant.
   */
  fun getManualPermissionInstructions(): String {
    return "To enable Breeze AI Assistant:\n" +
      "1. Go to Settings > Apps > Signal\n" +
      "2. Tap 'Display over other apps'\n" +
      "3. Turn on 'Allow display over other apps'\n" +
      "4. Return to Signal and try again"
  }
}
