package com.foxtrotalpha.reelsblocker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.lifecycleScope
import com.foxtrotalpha.reelsblocker.calendar.CalendarAccessUtils
import com.foxtrotalpha.reelsblocker.coach.CoachFragment
import com.foxtrotalpha.reelsblocker.data.IntegrationSync
import com.foxtrotalpha.reelsblocker.databinding.ActivityMainBinding
import com.foxtrotalpha.reelsblocker.health.HealthConnectPermissions
import com.foxtrotalpha.reelsblocker.ui.DashboardViewModel
import com.foxtrotalpha.reelsblocker.ui.InsightsFragment
import com.foxtrotalpha.reelsblocker.ui.TodayFragment
import com.foxtrotalpha.reelsblocker.usage.ScreenTimeSync
import com.foxtrotalpha.reelsblocker.usage.UsageAccessUtils
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mainContentInitialized = false
    private val dashboardViewModel: DashboardViewModel by viewModels()

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

        binding.usageAccessGate.enableUsageAccessButton.setOnClickListener {
            UsageAccessUtils.openUsageAccessSettings(this)
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.bottomNav.selectedItemId != R.id.navToday) {
                        selectTab(R.id.navToday)
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            },
        )

        requestNotificationPermissionIfNeeded()
        updateUsageAccessUi()
    }

    override fun onResume() {
        super.onResume()
        dashboardViewModel.refreshForToday()
        updateUsageAccessUi()
        refreshDashboardPermissions()
        refreshFocusProtection()
    }

    fun requestHealthConnectPermissions() {
        if (!HealthConnectPermissions.isHealthConnectAvailable(this)) {
            return
        }
        healthConnectPermissionLauncher.launch(HealthConnectPermissions.requiredPermissions())
    }

    fun requestCalendarPermission() {
        readCalendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
    }

    fun selectTab(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
    }

    suspend fun syncAllSources() {
        ScreenTimeSync.syncAllOnStartup(applicationContext)
        IntegrationSync.syncOnStartup(applicationContext)
        refreshDashboardPermissions()
        refreshFocusProtection()
    }

    private fun updateUsageAccessUi() {
        val usageAccessGranted = UsageAccessUtils.hasUsageAccess(this)

        if (!usageAccessGranted) {
            binding.usageAccessGate.root.visibility = View.VISIBLE
            binding.mainShell.visibility = View.GONE
            return
        }

        binding.usageAccessGate.root.visibility = View.GONE
        binding.mainShell.visibility = View.VISIBLE

        if (!mainContentInitialized) {
            setupMainContent()
            mainContentInitialized = true
        }

        refreshFocusProtection()
        syncScreenTimeOnStartup()
        syncIntegrationsOnStartup()
    }

    private fun setupMainContent() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        if (supportFragmentManager.findFragmentByTag(TAG_TODAY) == null) {
            showTab(R.id.navToday)
        } else {
            binding.bottomNav.selectedItemId = currentVisibleTab()
        }
    }

    private fun showTab(itemId: Int) {
        val transaction = supportFragmentManager.beginTransaction()
        listOf(TAG_TODAY, TAG_COACH, TAG_INSIGHTS).forEach { tag ->
            supportFragmentManager.findFragmentByTag(tag)?.let { transaction.hide(it) }
        }
        val tag = tagFor(itemId)
        val existing = supportFragmentManager.findFragmentByTag(tag)
        if (existing == null) {
            transaction.add(R.id.tabContainer, createFragment(itemId), tag)
        } else {
            transaction.show(existing)
        }
        transaction.commit()
    }

    private fun createFragment(itemId: Int): Fragment {
        return when (itemId) {
            R.id.navCoach -> CoachFragment()
            R.id.navInsights -> InsightsFragment()
            else -> TodayFragment()
        }
    }

    private fun tagFor(itemId: Int): String {
        return when (itemId) {
            R.id.navCoach -> TAG_COACH
            R.id.navInsights -> TAG_INSIGHTS
            else -> TAG_TODAY
        }
    }

    private fun currentVisibleTab(): Int {
        return when {
            supportFragmentManager.findFragmentByTag(TAG_COACH)?.isVisible == true -> R.id.navCoach
            supportFragmentManager.findFragmentByTag(TAG_INSIGHTS)?.isVisible == true -> R.id.navInsights
            else -> R.id.navToday
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

    private fun refreshFocusProtection() {
        dashboardViewModel.setFocusProtectionState(
            serviceEnabled = AccessibilityUtils.isServiceEnabled(this, ReelsBlockerService::class.java),
            blockingEnabled = BlockerPreferences.isBlockingEnabled(this),
        )
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

    companion object {
        const val TAG_TODAY = "tab_today"
        const val TAG_COACH = "tab_coach"
        const val TAG_INSIGHTS = "tab_insights"
    }
}
