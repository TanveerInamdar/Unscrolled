package com.foxtrotalpha.reelsblocker

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.foxtrotalpha.reelsblocker.detector.ReelsDetector

class ReelsBlockerService : AccessibilityService() {

    private var lastBlockTimestampMs = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) {
            return
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        if (!BlockerPreferences.isBlockingEnabled(this)) {
            return
        }

        val rootNode = rootInActiveWindow ?: return

        try {
            val result = ReelsDetector.evaluate(rootNode)
            if (result.shouldBlock) {
                maybeBlock(result.reason)
            }
        } finally {
            rootNode.recycle()
        }
    }

    private fun maybeBlock(reason: ReelsDetector.Result.Reason?) {
        val now = System.currentTimeMillis()
        if (now - lastBlockTimestampMs < BLOCK_COOLDOWN_MS) {
            return
        }
        lastBlockTimestampMs = now

        performGlobalAction(GLOBAL_ACTION_BACK)

        BlockFeedback.showBlocked(applicationContext, reason)
    }

    override fun onInterrupt() {
        // No-op
    }

    companion object {
        private const val BLOCK_COOLDOWN_MS = 1500L
    }
}
