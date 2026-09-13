package com.foxtrotalpha.reelsblocker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.foxtrotalpha.reelsblocker.calendar.CalendarAccessUtils
import com.foxtrotalpha.reelsblocker.data.IntegrationSync
import com.foxtrotalpha.reelsblocker.databinding.ActivityMainBinding
import com.foxtrotalpha.reelsblocker.health.HealthConnectPermissions
import com.foxtrotalpha.reelsblocker.ui.DashboardBinder
import com.foxtrotalpha.reelsblocker.ui.DashboardViewModel
import com.foxtrotalpha.reelsblocker.usage.ScreenTimeSync
import com.foxtrotalpha.reelsblocker.usage.UsageAccessUtils
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mainContentInitialized = false
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private lateinit var dashboardBinder: DashboardBinder

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

        dashboardBinder = DashboardBinder(
            context = this,
            binding = binding,
            onRequestHealthPermissions = { requestHealthConnectPermissions() },
            onRequestCalendarPermission = { requestCalendarPermission() },
        )

        requestNotificationPermissionIfNeeded()
        updateUsageAccessUi()
    }

    override fun onResume() {
        super.onResume()
        updateUsageAccessUi()
        refreshDashboardPermissions()
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

        setupDashboard()
    }

    private fun setupDashboard() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                dashboardViewModel.state.collect { state ->
                    dashboardBinder.bind(state)
                }
            }
        }
    }

    private fun refreshDashboardPermissions() {
        val hcAvailable = HealthConnectPermissions.isHealthConnectAvailable(this)
        dashboardViewModel.setHealthConnectAvailable(hcAvailable)
        dashboardViewModel.setCalendarPermissionGranted(
            CalendarAccessUtils.hasReadCalendarPermission(this),
        )

        if (!hcAvailable) {
            dashboardViewModel.setHealthPermissionGranted(false)
            return
        }

        lifecycleScope.launch {
            val client = HealthConnectClient.getOrCreate(applicationContext)
            dashboardViewModel.setHealthPermissionGranted(
                HealthConnectPermissions.hasAnyPermission(client),
            )
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
