package com.foxtrotalpha.reelsblocker.detector

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Detects Instagram Reels using accessibility metadata and screen regions.
 *
 * Message text in DMs is ignored by only matching headers in the top screen region
 * or bottom navigation tabs in the bottom region.
 */
object ReelsDetector {

    data class Result(
        val shouldBlock: Boolean,
        val reason: Reason? = null,
    ) {
        enum class Reason {
            REELS_TAB_SELECTED,
            SUGGESTED_REELS_FEED,
            DM_REELS_HEADER_SELECTED,
        }
    }

    private val dmFeedHeaderLabels = listOf(
        "Suggested",
        "Reels",
    )

    private val bottomNavTabLabels = listOf(
        "Home",
        "Search",
        "Search and Explore",
        "Explore",
        "Reels",
        "Shop",
        "Profile",
    )

    private val safeSelectedTabLabels = listOf(
        "Home",
        "Search",
        "Search and Explore",
        "Explore",
    )

    private const val TOP_REGION_RATIO = 0.22f
    private const val BOTTOM_REGION_RATIO = 0.18f

    fun evaluate(root: AccessibilityNodeInfo?): Result {
        if (root == null) {
            return Result(shouldBlock = false)
        }

        if (isSafeContext(root)) {
            return Result(shouldBlock = false)
        }

        if (isStoryViewer(root)) {
            return Result(shouldBlock = false)
        }

        if (isReelsTabActive(root)) {
            return Result(shouldBlock = true, reason = Result.Reason.REELS_TAB_SELECTED)
        }

        if (isDmReelsFeedActive(root)) {
            val reason = if (hasTopScreenHeader(root, "Suggested")) {
                Result.Reason.SUGGESTED_REELS_FEED
            } else {
                Result.Reason.DM_REELS_HEADER_SELECTED
            }
            return Result(shouldBlock = true, reason = reason)
        }

        return Result(shouldBlock = false)
    }

    fun isSafeContext(root: AccessibilityNodeInfo): Boolean {
        return safeSelectedTabLabels.any { label ->
            findSelectedNavTab(root, label) != null
        }
    }

    /**
     * Instagram Stories use a close button and reply UI, not Reels comment/audio controls.
     */
    fun isStoryViewer(root: AccessibilityNodeInfo): Boolean {
        if (hasBottomNavigation(root)) {
            return false
        }

        val hasClose = hasContentDescription(root, "Close") ||
            hasContentDescriptionContaining(root, "Close")
        val hasSendMessage = hasContentDescriptionContaining(root, "Send message") ||
            hasContentDescriptionContaining(root, "Reply")
        val hasReelComment = hasContentDescription(root, "Comment")
        val hasReelAudio = hasContentDescriptionContaining(root, "audio")

        return (hasClose || hasSendMessage) && !hasReelComment && !hasReelAudio
    }

    fun isReelsTabActive(root: AccessibilityNodeInfo): Boolean {
        val reelsTab = findSelectedNavTab(root, "Reels") ?: return false
        return isInBottomRegion(reelsTab, root)
    }

    /**
     * DM/link viewer after scrolling past the single shared Reel.
     * Matches a top-of-screen "Suggested" or "Reels" header when bottom nav is hidden.
     */
    fun isDmReelsFeedActive(root: AccessibilityNodeInfo): Boolean {
        if (hasBottomNavigation(root)) {
            return false
        }

        return dmFeedHeaderLabels.any { label ->
            hasTopScreenHeader(root, label)
        }
    }

    fun hasTopScreenHeader(root: AccessibilityNodeInfo, label: String): Boolean {
        if (hasSelectedHeader(root, label)) {
            return true
        }

        return walkTree(root) { node ->
            if (node.isEditable || !isInTopRegion(node, root)) {
                return@walkTree false
            }

            val description = node.contentDescription?.toString()?.trim().orEmpty()
            val text = node.text?.toString()?.trim().orEmpty()

            matchesHeaderLabel(label, description, text)
        }
    }

    fun hasBottomNavigation(root: AccessibilityNodeInfo): Boolean {
        var matches = 0
        for (label in bottomNavTabLabels) {
            val tab = findNavTab(root, label) ?: continue
            if (isInBottomRegion(tab, root)) {
                matches++
            }
        }
        return matches >= 2
    }

    fun findSelectedNavTab(root: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        val tab = findNavTab(root, label) ?: return null
        if (!tab.isSelected || !isInBottomRegion(tab, root)) {
            return null
        }
        return tab
    }

    fun hasSelectedHeader(root: AccessibilityNodeInfo, label: String): Boolean {
        return walkTree(root) { node ->
            if (node.isEditable) {
                return@walkTree false
            }

            val description = node.contentDescription?.toString()?.trim().orEmpty()
            node.isSelected && matchesHeaderLabel(label, description, "")
        }
    }

    fun findNavTab(root: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        walkTree(root) { node ->
            if (found != null) {
                return@walkTree true
            }

            if (!node.isClickable || node.isEditable) {
                return@walkTree false
            }

            val description = node.contentDescription?.toString()?.trim().orEmpty()
            if (description.equals(label, ignoreCase = true)) {
                found = node
                true
            } else {
                false
            }
        }
        return found
    }

    private fun matchesHeaderLabel(label: String, description: String, text: String): Boolean {
        return when (label) {
            "Suggested" -> description.contains("Suggested", ignoreCase = true) ||
                text.equals("Suggested", ignoreCase = true)
            else -> description.equals(label, ignoreCase = true) ||
                text.equals(label, ignoreCase = true)
        }
    }

    private fun isInTopRegion(node: AccessibilityNodeInfo, root: AccessibilityNodeInfo): Boolean {
        val rootRect = Rect()
        val nodeRect = Rect()
        root.getBoundsInScreen(rootRect)
        node.getBoundsInScreen(nodeRect)

        if (rootRect.height() <= 0) {
            return false
        }

        val topThreshold = rootRect.top + (rootRect.height() * TOP_REGION_RATIO).toInt()
        return nodeRect.centerY() <= topThreshold
    }

    private fun isInBottomRegion(node: AccessibilityNodeInfo, root: AccessibilityNodeInfo): Boolean {
        val rootRect = Rect()
        val nodeRect = Rect()
        root.getBoundsInScreen(rootRect)
        node.getBoundsInScreen(nodeRect)

        if (rootRect.height() <= 0) {
            return false
        }

        val bottomThreshold = rootRect.bottom - (rootRect.height() * BOTTOM_REGION_RATIO).toInt()
        return nodeRect.centerY() >= bottomThreshold
    }

    private fun hasContentDescription(root: AccessibilityNodeInfo, targetWord: String): Boolean {
        return walkTree(root) { node ->
            node.contentDescription?.toString()?.trim().equals(targetWord, ignoreCase = true) == true
        }
    }

    private fun hasContentDescriptionContaining(root: AccessibilityNodeInfo, targetWord: String): Boolean {
        return walkTree(root) { node ->
            node.contentDescription?.toString()?.contains(targetWord, ignoreCase = true) == true
        }
    }

    private fun walkTree(
        node: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): Boolean {
        if (node == null) {
            return false
        }

        if (predicate(node)) {
            return true
        }

        for (index in 0 until node.childCount) {
            if (walkTree(node.getChild(index), predicate)) {
                return true
            }
        }

        return false
    }
}
