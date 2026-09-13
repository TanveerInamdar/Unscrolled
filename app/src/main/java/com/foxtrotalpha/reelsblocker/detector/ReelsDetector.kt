package com.foxtrotalpha.reelsblocker.detector

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Detects short-form video players (Instagram Reels, YouTube Shorts) via the
 * apps' internal view IDs.
 *
 * Instagram internally calls Reels "clips": every immersive Reels player —
 * the Reels tab, For You / Explore reels, and reels opened from DMs or links —
 * is hosted inside `clips_viewer_view_pager`. Stories use a completely
 * different set of view IDs (`reel_viewer_*`, confusingly), so they never
 * match this check.
 *
 * YouTube internally calls Shorts "reels": every Shorts player — the Shorts
 * tab, home feed shelf, search results, links, and channel pages — is hosted
 * inside `reel_player_page_container`.
 *
 * Note: recent Instagram builds keep the clips viewer node in the tree at all
 * times with zero size, so a match requires `isVisibleToUser` and non-zero
 * on-screen bounds.
 */
object ReelsDetector {

    data class Result(
        val shouldBlock: Boolean,
        val reason: Reason? = null,
    ) {
        enum class Reason {
            PLAYER_VISIBLE,
            TAB_SELECTED,
        }
    }

    /**
     * Detection rule for one app.
     *
     * @param playerViewIds Unqualified view IDs of the short-form player
     * container. Qualified at lookup time as `<package>:id/<id>` so modified
     * variants (e.g. ReVanced) resolve against their own package name.
     * @param tabLabel Bottom-navigation tab label used as a fallback if the
     * app renames its player view ID.
     * @param tabLabelIsPrefix Match the label as a prefix (YouTube may append
     * badge text to the tab's content description).
     */
    private data class AppRule(
        val playerViewIds: List<String>,
        val tabLabel: String,
        val tabLabelIsPrefix: Boolean = false,
    )

    private val instagramRule = AppRule(
        playerViewIds = listOf("clips_viewer_view_pager"),
        tabLabel = "Reels",
    )

    private val youtubeRule = AppRule(
        playerViewIds = listOf("reel_player_page_container"),
        tabLabel = "Shorts",
        tabLabelIsPrefix = true,
    )

    private val rulesByPackage = mapOf(
        "com.instagram.android" to instagramRule,
        "com.google.android.youtube" to youtubeRule,
        "com.google.android.apps.youtube.kids" to youtubeRule,
        "app.revanced.android.youtube" to youtubeRule,
    )

    private const val BOTTOM_REGION_RATIO = 0.18f

    fun evaluate(root: AccessibilityNodeInfo?): Result {
        if (root == null) {
            return Result(shouldBlock = false)
        }

        val packageName = root.packageName?.toString() ?: return Result(shouldBlock = false)
        val rule = rulesByPackage[packageName] ?: return Result(shouldBlock = false)

        if (isPlayerVisible(root, packageName, rule)) {
            return Result(shouldBlock = true, reason = Result.Reason.PLAYER_VISIBLE)
        }

        // Fallback in case the app renames its player view ID: the selected
        // Reels/Shorts tab in the bottom navigation bar.
        if (isBlockedTabActive(root, rule)) {
            return Result(shouldBlock = true, reason = Result.Reason.TAB_SELECTED)
        }

        return Result(shouldBlock = false)
    }

    /**
     * True when a short-form player container is actually on screen.
     */
    private fun isPlayerVisible(
        root: AccessibilityNodeInfo,
        packageName: String,
        rule: AppRule,
    ): Boolean {
        return rule.playerViewIds.any { viewId ->
            val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/$viewId")
                ?: return@any false
            nodes.any { node ->
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                node.isVisibleToUser && bounds.width() > 0 && bounds.height() > 0
            }
        }
    }

    private fun isBlockedTabActive(root: AccessibilityNodeInfo, rule: AppRule): Boolean {
        val tab = findNavTab(root, rule.tabLabel, rule.tabLabelIsPrefix) ?: return false
        return tab.isSelected && isInBottomRegion(tab, root)
    }

    private fun findNavTab(
        root: AccessibilityNodeInfo,
        label: String,
        matchPrefix: Boolean,
    ): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        walkTree(root) { node ->
            if (found != null) {
                return@walkTree true
            }

            if (!node.isClickable || node.isEditable) {
                return@walkTree false
            }

            val description = node.contentDescription?.toString()?.trim().orEmpty()
            val matches = if (matchPrefix) {
                description.startsWith(label, ignoreCase = true)
            } else {
                description.equals(label, ignoreCase = true)
            }

            if (matches) {
                found = node
                true
            } else {
                false
            }
        }
        return found
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
