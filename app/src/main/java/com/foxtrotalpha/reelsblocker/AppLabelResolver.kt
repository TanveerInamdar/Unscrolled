package com.foxtrotalpha.reelsblocker

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process

/**
 * Resolves the user-visible name for an installed (or recently used) app.
 *
 * Android exposes two labels per app:
 * - **Application label** — [PackageManager.getApplicationLabel], from the
 *   manifest `<application android:label>`. Some publishers set an internal
 *   codename here (e.g. "Nexus client" for YouTube Music).
 * - **Launcher activity label** — what the home screen shows under the icon.
 *   This is the name users recognize, so we prefer it.
 */
object AppLabelResolver {

    private val RESOLVE_FLAGS = PackageManager.MATCH_DEFAULT_ONLY or
        PackageManager.MATCH_DISABLED_COMPONENTS or
        PackageManager.MATCH_UNINSTALLED_PACKAGES

    private val GENERIC_PACKAGE_SUFFIXES = setOf(
        "android",
        "app",
        "apps",
        "mobile",
        "client",
        "katana",
    )

    private val PACKAGE_SEGMENT_IGNORE = setOf(
        "com",
        "org",
        "net",
        "io",
        "co",
        "android",
        "google",
        "apps",
        "app",
    )

    fun resolve(context: Context, packageName: String): String {
        launcherLabel(context, packageName)?.let { return it }

        val applicationLabel = applicationLabel(context, packageName)
        if (applicationLabel != null && !looksLikeInternalLabel(applicationLabel)) {
            return applicationLabel
        }

        fallbackLabel(packageName).takeIf { it.isNotBlank() }?.let { return it }

        return applicationLabel ?: packageName
    }

    private fun launcherLabel(context: Context, packageName: String): String? {
        val packageManager = context.packageManager

        packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            packageManager.resolveActivity(launchIntent, RESOLVE_FLAGS)
                ?.loadLabel(packageManager)
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName)
        for (activity in packageManager.queryIntentActivities(launcherIntent, RESOLVE_FLAGS)) {
            val label = activity.loadLabel(packageManager).toString().trim()
            if (label.isNotBlank()) {
                return label
            }
        }

        val launcherApps = context.getSystemService(LauncherApps::class.java)
        if (launcherApps != null) {
            try {
                for (activity in launcherApps.getActivityList(packageName, Process.myUserHandle())) {
                    val label = activity.label?.toString()?.trim()
                    if (!label.isNullOrBlank()) {
                        return label
                    }
                }
            } catch (_: Exception) {
                // Package may not be installed for this user profile.
            }
        }

        return null
    }

    private fun applicationLabel(context: Context, packageName: String): String? {
        val packageManager = context.packageManager
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, RESOLVE_FLAGS)
            packageManager.getApplicationLabel(applicationInfo).toString().trim()
                .takeIf { it.isNotBlank() }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    /** Manifest label looks like an engineering codename rather than a product name. */
    private fun looksLikeInternalLabel(label: String): Boolean {
        val lower = label.lowercase()
        return lower.contains("nexus") ||
            lower.endsWith(" client") ||
            lower == "client" ||
            lower == "mediaclient"
    }

    private fun fallbackLabel(packageName: String): String {
        val segments = meaningfulSegments(packageName)
        if (segments.isEmpty()) {
            return packageName.substringAfterLast('.')
        }

        return segments.joinToString(" ") { segment ->
            segment.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
        }
    }

    private fun meaningfulSegments(packageName: String): List<String> {
        val tokens = packageName.split('.')
        if (tokens.size <= 1) {
            return emptyList()
        }

        val meaningful = tokens.filter { token ->
            token.isNotBlank() &&
                token !in PACKAGE_SEGMENT_IGNORE &&
                token !in GENERIC_PACKAGE_SUFFIXES &&
                !token.endsWith("client")
        }

        return if (meaningful.size >= 2) {
            meaningful.takeLast(2)
        } else {
            meaningful
        }
    }
}
