package com.foxtrotalpha.reelsblocker.settings

import android.Manifest
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.lifecycleScope
import com.foxtrotalpha.reelsblocker.AccessibilityUtils
import com.foxtrotalpha.reelsblocker.BlockerPreferences
import com.foxtrotalpha.reelsblocker.BuildConfig
import com.foxtrotalpha.reelsblocker.R
import com.foxtrotalpha.reelsblocker.ReelsBlockerService
import com.foxtrotalpha.reelsblocker.calendar.CalendarAccessUtils
import com.foxtrotalpha.reelsblocker.data.AppDatabase
import com.foxtrotalpha.reelsblocker.databinding.ActivitySettingsBinding
import com.foxtrotalpha.reelsblocker.health.HealthConnectPermissions
import com.foxtrotalpha.reelsblocker.usage.UsageAccessUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val healthConnectPermissionLauncher = registerForActivityResult(
        HealthConnectPermissions.createPermissionContract(),
    ) { refreshStatuses() }

    private val readCalendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshStatuses() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.settingsToolbar.setNavigationOnClickListener { finish() }

        binding.settingsBlockingSwitch.isChecked = BlockerPreferences.isBlockingEnabled(this)
        binding.settingsBlockingSwitch.setOnCheckedChangeListener { _, isChecked ->
            BlockerPreferences.setBlockingEnabled(this, isChecked)
        }
        binding.settingsVoiceRoastSwitch.isChecked = BlockerPreferences.isVoiceRoastEnabled(this)
        binding.settingsVoiceRoastSwitch.setOnCheckedChangeListener { _, isChecked ->
            BlockerPreferences.setVoiceRoastEnabled(this, isChecked)
        }
        binding.settingsProfanitySwitch.isChecked = BlockerPreferences.isProfanityEnabled(this)
        binding.settingsProfanitySwitch.setOnCheckedChangeListener { _, isChecked ->
            BlockerPreferences.setProfanityEnabled(this, isChecked)
        }
        binding.settingsOpenAccessibility.setOnClickListener {
            AccessibilityUtils.openAccessibilitySettings(this)
        }
        binding.settingsHealthButton.setOnClickListener {
            if (HealthConnectPermissions.isHealthConnectAvailable(this)) {
                healthConnectPermissionLauncher.launch(HealthConnectPermissions.requiredPermissions())
            }
        }
        binding.settingsUsageButton.setOnClickListener {
            UsageAccessUtils.openUsageAccessSettings(this)
        }
        binding.settingsCalendarButton.setOnClickListener {
            readCalendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
        }
        binding.settingsVersion.text = getString(R.string.settings_version, BuildConfig.VERSION_NAME)

        lifecycleScope.launch {
            AppDatabase.get(this@SettingsActivity).trackedAppDao().observeAll().collectLatest { apps ->
                binding.settingsTrackedApps.removeAllViews()
                apps.forEach { app ->
                    val row = TextView(this@SettingsActivity).apply {
                        text = app.label
                        setTextAppearance(R.style.TextAppearance_Zen_Caption)
                        setPadding(0, 8, 0, 8)
                    }
                    binding.settingsTrackedApps.addView(row)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatuses()
    }

    private fun refreshStatuses() {
        val serviceOn = AccessibilityUtils.isServiceEnabled(this, ReelsBlockerService::class.java)
        binding.settingsAccessibilityStatus.text = if (serviceOn) {
            getString(R.string.status_service_on)
        } else {
            getString(R.string.status_service_off)
        }
        binding.settingsUsageStatus.text = if (UsageAccessUtils.hasUsageAccess(this)) {
            "${getString(R.string.settings_usage_access)} · ${getString(R.string.settings_connected)}"
        } else {
            "${getString(R.string.settings_usage_access)} · ${getString(R.string.settings_not_connected)}"
        }
        binding.settingsCalendarStatus.text = if (CalendarAccessUtils.hasReadCalendarPermission(this)) {
            "${getString(R.string.settings_calendar)} · ${getString(R.string.settings_connected)}"
        } else {
            "${getString(R.string.settings_calendar)} · ${getString(R.string.settings_not_connected)}"
        }
        binding.settingsBackboardStatus.text =
            "${getString(R.string.settings_backboard)} · ${configured(BuildConfig.BACKBOARD_API_KEY.isNotBlank())}"
        binding.settingsGeminiStatus.text =
            "${getString(R.string.settings_gemini)} · ${configured(BuildConfig.GEMINI_API_KEY.isNotBlank())}"
        binding.settingsElevenLabsStatus.text =
            "${getString(R.string.settings_elevenlabs)} · ${configured(BuildConfig.ELEVENLABS_API_KEY.isNotBlank())}"

        if (!HealthConnectPermissions.isHealthConnectAvailable(this)) {
            binding.settingsHealthStatus.text =
                "${getString(R.string.settings_health_connect)} · ${getString(R.string.settings_not_connected)}"
            return
        }
        lifecycleScope.launch {
            val granted = HealthConnectPermissions.hasAnyPermission(
                HealthConnectClient.getOrCreate(applicationContext),
            )
            binding.settingsHealthStatus.text = if (granted) {
                "${getString(R.string.settings_health_connect)} · ${getString(R.string.settings_connected)}"
            } else {
                "${getString(R.string.settings_health_connect)} · ${getString(R.string.settings_not_connected)}"
            }
        }
    }

    private fun configured(value: Boolean): String {
        return getString(if (value) R.string.settings_configured else R.string.settings_not_configured)
    }
}
