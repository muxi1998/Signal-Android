package com.mtkresearch.breeze

import android.content.Context

/**
 * Stub PermissionManager used in non‑breezePlay builds.
 * It simply reports that all required permissions are granted.
 */
class PermissionManager(context: Context) {
    fun hasRequiredPermissions(): Boolean = true
}
