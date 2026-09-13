package com.foxtrotalpha.reelsblocker

import android.os.Bundle
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.foxtrotalpha.reelsblocker.databinding.ActivityMainBinding
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.foxtrotalpha.reelsblocker.data.AppDatabase
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* optional — blocking still works without it */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.blockingSwitch.isChecked = BlockerPreferences.isBlockingEnabled(this)
        binding.blockingSwitch.setOnCheckedChangeListener { _, isChecked ->
            BlockerPreferences.setBlockingEnabled(this, isChecked)
            refreshStatus()
        }

        binding.openSettingsButton.setOnClickListener {
            AccessibilityUtils.openAccessibilitySettings(this)
        }

        refreshStatus()
        requestNotificationPermissionIfNeeded()
        observeBlocksToday()
    }

    private fun observeBlocksToday() {
        val dao = AppDatabase.get(applicationContext).blockEventDao()
        lifecycleScope.launch {
            dao.countForDate(AppDatabase.isoDate()).collect { count ->
                binding.blocksTodayText.text = if (count == 0) {
                    getString(R.string.blocks_today_zero)
                } else {
                    resources.getQuantityString(R.plurals.blocks_today, count, count)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val serviceEnabled = AccessibilityUtils.isServiceEnabled(this, ReelsBlockerService::class.java)
        val blockingEnabled = BlockerPreferences.isBlockingEnabled(this)

        binding.serviceStatusText.text = if (serviceEnabled) {
            getString(R.string.status_service_on)
        } else {
            getString(R.string.status_service_off)
        }

        binding.blockingStatusText.text = when {
            !serviceEnabled -> getString(R.string.status_service_off)
            blockingEnabled -> getString(R.string.status_enabled)
            else -> getString(R.string.status_disabled)
        }

        val statusColor = when {
            serviceEnabled && blockingEnabled -> R.color.success
            serviceEnabled -> R.color.warning
            else -> R.color.warning
        }

        binding.blockingStatusText.setTextColor(
            ContextCompat.getColor(this, statusColor),
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
