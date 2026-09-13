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
import com.foxtrotalpha.reelsblocker.data.IntegrationSync
import com.foxtrotalpha.reelsblocker.databinding.ActivityMainBinding
import com.foxtrotalpha.reelsblocker.health.HealthConnectPermissions
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

    private val healthConnectPermissionLauncher = registerForActivityResult(
        HealthConnectPermissions.createPermissionContract(),
    ) { /* sync runs on next onResume */ }

    private val readCalendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* sync runs on next onResume */ }

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
        syncIntegrationsOnStartup()
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
            binding.requestHealthPermissionsButton.visibility = View.VISIBLE
            binding.requestHealthPermissionsButton.setOnClickListener {
                requestHealthConnectPermissions()
            }
            binding.requestCalendarPermissionButton.visibility = View.VISIBLE
            binding.requestCalendarPermissionButton.setOnClickListener {
                requestCalendarPermission()
            }
            setupDevDatabasePanel()
        }
    }

    private fun setupDevDatabasePanel() {
        val database = AppDatabase.get(applicationContext)
        val trackedDao = database.trackedAppDao()
        val blockDao = database.blockEventDao()
        val usageDao = database.dailyAppUsageDao()
        val healthDao = database.dailyHealthMetricsDao()
        val sleepDao = database.sleepSessionDao()
        val calendarDao = database.calendarEventDao()

        lifecycleScope.launch {
            combine(
                combine(
                    trackedDao.observeAll(),
                    blockDao.observeAll(),
                    usageDao.observeAll(),
                    healthDao.observeAll(),
                    sleepDao.observeAll(),
                ) { trackedApps, blockEvents, dailyUsage, healthMetrics, sleepSessions ->
                    DevDatabaseFormatter.Snapshot(
                        trackedApps = trackedApps,
                        blockEvents = blockEvents,
                        dailyUsage = dailyUsage,
                        healthMetrics = healthMetrics,
                        sleepSessions = sleepSessions,
                        calendarEvents = emptyList(),
                    )
                },
                calendarDao.observeAll(),
            ) { snapshot, calendarEvents ->
                snapshot.copy(calendarEvents = calendarEvents)
            }.collect { snapshot ->
                binding.devDatabaseSummaryText.text = getString(
                    R.string.dev_database_summary,
                    snapshot.trackedApps.size,
                    snapshot.blockEvents.size,
                    snapshot.dailyUsage.size,
                    snapshot.healthMetrics.size,
                    snapshot.sleepSessions.size,
                    snapshot.calendarEvents.size,
                )
                binding.devDatabaseDumpText.text = DevDatabaseFormatter.format(
                    this@MainActivity,
                    snapshot,
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

    private fun syncIntegrationsOnStartup() {
        lifecycleScope.launch {
            IntegrationSync.syncOnStartup(applicationContext)
        }
    }

    private fun requestHealthConnectPermissions() {
        if (!HealthConnectPermissions.isHealthConnectAvailable(this)) {
            return
        }
        healthConnectPermissionLauncher.launch(HealthConnectPermissions.requiredPermissions())
    }

    private fun requestCalendarPermission() {
        readCalendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
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
