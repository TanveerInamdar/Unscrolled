package com.foxtrotalpha.reelsblocker

import android.content.Context
import android.content.pm.PackageManager

object AppLabelResolver {

    private val GENERIC_PACKAGE_SUFFIXES = setOf(
        "android",
        "app",
        "mobile",
        "client",
        "katana",
    )

    fun resolve(context: Context, packageName: String): String {
        val packageManager = context.packageManager
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            fallbackLabel(packageName)
        }
    }

    private fun fallbackLabel(packageName: String): String {
        val parts = packageName.split('.')
        if (parts.size >= 2 && parts.last() in GENERIC_PACKAGE_SUFFIXES) {
            return parts[parts.size - 2]
        }
        return parts.lastOrNull() ?: packageName
    }
}
