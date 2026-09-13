package com.foxtrotalpha.reelsblocker

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.foxtrotalpha.reelsblocker.data.AppDatabase
import com.foxtrotalpha.reelsblocker.data.BlockEvent
import com.foxtrotalpha.reelsblocker.detector.ReelsDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ReelsBlockerService : AccessibilityService() {

    private var lastBlockTimestampMs = 0L

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                val packageName = rootNode.packageName?.toString() ?: "unknown"
                maybeBlock(result.reason, packageName)
            }
        } finally {
            @Suppress("DEPRECATION")
            rootNode.recycle()
        }
    }

    private fun maybeBlock(reason: ReelsDetector.Result.Reason?, packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastBlockTimestampMs < BLOCK_COOLDOWN_MS) {
            return
        }
        lastBlockTimestampMs = now

        performGlobalAction(GLOBAL_ACTION_BACK)

        BlockFeedback.showBlocked(applicationContext, reason)

        logBlockEvent(now, packageName, reason)
    }

    private fun logBlockEvent(
        timestampMs: Long,
        packageName: String,
        reason: ReelsDetector.Result.Reason?,
    ) {
        val event = BlockEvent(
            timestampMs = timestampMs,
            date = AppDatabase.isoDate(timestampMs),
            packageName = packageName,
            reason = reason?.name ?: "UNKNOWN",
        )
        serviceScope.launch {
            AppDatabase.get(applicationContext).blockEventDao().insert(event)
        }
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val BLOCK_COOLDOWN_MS = 1500L
    }
}
