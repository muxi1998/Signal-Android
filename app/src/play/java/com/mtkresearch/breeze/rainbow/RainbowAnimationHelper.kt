package com.mtkresearch.breeze.rainbow

import android.view.View
import org.thoughtcrime.securesms.conversation.v2.ConversationFragment

/**
 * Stub implementation of RainbowAnimationHelper for builds that do not include the real Breeze module.
 * Provides no‑op methods that satisfy the compiler.
 */
class RainbowAnimationHelper private constructor() {
  companion object {
    /**
     * Mimics the real API: creates a helper instance and starts the rainbow animation.
     * In the stub implementation this does nothing and simply returns a new instance.
     */
    fun applyRainbowAfterAIInjection(view: View, fragment: ConversationFragment): RainbowAnimationHelper {
      // No animation in stub – just return a new helper.
      return RainbowAnimationHelper()
    }
  }

  /**
   * Stub cleanup method – does nothing.
   */
  fun cleanup() {
    // No resources to release in the stub.
  }
}
