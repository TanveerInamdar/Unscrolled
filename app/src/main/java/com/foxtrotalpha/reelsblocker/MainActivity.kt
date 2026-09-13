package com.foxtrotalpha.reelsblocker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.foxtrotalpha.reelsblocker.data.AppDatabase
import com.foxtrotalpha.reelsblocker.data.DevDatabaseFormatter
import com.foxtrotalpha.reelsblocker.databinding.ActivityMainBinding
import com.foxtrotalpha.reelsblocker.usage.ScreenTimeSync
import com.foxtrotalpha.reelsblocker.usage.UsageAccessUtils
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mainContentInitialized = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* optional — blocking still works without it */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIfNeeded()
        updateUsageAccessUi()
    }

    override fun onResume() {
        super.onResume()
        updateUsageAccessUi()
    }

    private fun updateUsageAccessUi() {
        val usageAccessGranted = UsageAccessUtils.hasUsageAccess(this)

        if (!usageAccessGranted) {
            binding.blankScreen.visibility = View.VISIBLE
            binding.mainContent.visibility = View.GONE
            return
        }

        binding.blankScreen.visibility = View.GONE
        binding.mainContent.visibility = View.VISIBLE

        if (!mainContentInitialized) {
            setupMainContent()
            mainContentInitialized = true
        }

        refreshStatus()
        syncScreenTimeOnStartup()
    }

    private fun setupMainContent() {
        binding.blockingSwitch.isChecked = BlockerPreferences.isBlockingEnabled(this)
        binding.blockingSwitch.setOnCheckedChangeListener { _, isChecked ->
            BlockerPreferences.setBlockingEnabled(this, isChecked)
            refreshStatus()
        }

        binding.openSettingsButton.setOnClickListener {
            AccessibilityUtils.openAccessibilitySettings(this)
        }

        observeBlocksToday()

        if (BuildConfig.DEBUG) {
            binding.devDatabaseCard.visibility = View.VISIBLE
            setupDevDatabasePanel()
        }
    }

    private fun setupDevDatabasePanel() {
        val database = AppDatabase.get(applicationContext)
        val trackedDao = database.trackedAppDao()
        val blockDao = database.blockEventDao()
        val usageDao = database.dailyAppUsageDao()

        lifecycleScope.launch {
            combine(
                trackedDao.observeAll(),
                blockDao.observeAll(),
                usageDao.observeAll(),
            ) { trackedApps, blockEvents, dailyUsage ->
                Triple(trackedApps, blockEvents, dailyUsage)
            }.collect { (trackedApps, blockEvents, dailyUsage) ->
                binding.devDatabaseSummaryText.text = getString(
                    R.string.dev_database_summary,
                    trackedApps.size,
                    blockEvents.size,
                    dailyUsage.size,
                )
                binding.devDatabaseDumpText.text = DevDatabaseFormatter.format(
                    this@MainActivity,
                    trackedApps,
                    blockEvents,
                    dailyUsage,
                )
            }
        }
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

    private fun syncScreenTimeOnStartup() {
        lifecycleScope.launch {
            ScreenTimeSync.syncAllOnStartup(applicationContext)
        }
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

        binding.usageStatusText.text = getString(R.string.status_usage_on)

        val statusColor = when {
            serviceEnabled && blockingEnabled -> R.color.success
            serviceEnabled -> R.color.warning
            else -> R.color.warning
        }

        binding.blockingStatusText.setTextColor(
            ContextCompat.getColor(this, statusColor),
        )

        binding.usageStatusText.setTextColor(
            ContextCompat.getColor(this, R.color.success),
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
