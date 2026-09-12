package com.foxtrotalpha.reelsblocker

import android.content.Context
import android.content.SharedPreferences

object BlockerPreferences {
    private const val PREFS_NAME = "reels_blocker_prefs"
    private const val KEY_BLOCKING_ENABLED = "blocking_enabled"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isBlockingEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_BLOCKING_ENABLED, true)
    }

    fun setBlockingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BLOCKING_ENABLED, enabled).apply()
    }
}
