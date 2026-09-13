package com.foxtrotalpha.reelsblocker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.foxtrotalpha.reelsblocker.detector.ReelsDetector

object BlockFeedback {

    private const val CHANNEL_ID = "reels_blocked"
    private const val NOTIFICATION_ID = 1001

    fun showBlocked(context: Context, reason: ReelsDetector.Result.Reason?) {
        val reasonLabel = reasonLabel(context, reason)
        vibrate(context)
        showNotification(context, reasonLabel)

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context.applicationContext,
                context.getString(R.string.notification_blocked_with_reason, reasonLabel),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun reasonLabel(context: Context, reason: ReelsDetector.Result.Reason?): String {
        return when (reason) {
            ReelsDetector.Result.Reason.REELS_TAB_SELECTED ->
                context.getString(R.string.reason_reels_tab)
            ReelsDetector.Result.Reason.SUGGESTED_REELS_FEED ->
                context.getString(R.string.reason_suggested)
            ReelsDetector.Result.Reason.DM_REELS_HEADER_SELECTED ->
                context.getString(R.string.reason_dm_reels)
            null -> context.getString(R.string.reason_unknown)
        }
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java) ?: return
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }

    private fun showNotification(context: Context, reasonLabel: String) {
        createChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.notification_blocked_title))
            .setContentText(context.getString(R.string.notification_blocked_with_reason, reasonLabel))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setTimeoutAfter(3000)
            .build()

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }

        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }
}
