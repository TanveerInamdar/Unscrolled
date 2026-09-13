package com.foxtrotalpha.reelsblocker.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.foxtrotalpha.reelsblocker.R
import com.foxtrotalpha.reelsblocker.databinding.FragmentTodayBinding
import com.foxtrotalpha.reelsblocker.databinding.ItemCalendarRowBinding

class DashboardBinder(
    private val context: Context,
    private val binding: FragmentTodayBinding,
    private val onRequestHealthPermissions: () -> Unit,
    private val onRequestCalendarPermission: () -> Unit,
    private val onOpenAccessibilitySettings: () -> Unit,
    private val onBlockingToggled: (Boolean) -> Unit,
) {

    fun bind(state: DashboardState) {
        binding.todayDateText.text = state.todayDateLabel
        binding.todayGreetingText.text = state.greeting
        binding.heroSummaryText.text = state.heroSummary
        binding.heroStepsValue.text = if (state.hasStepData) {
            DashboardFormatter.formatSteps(state.stepsToday)
        } else {
            context.getString(R.string.metric_unavailable)
        }
        binding.heroUnproductiveValue.text = DashboardFormatter.formatDurationMs(state.unproductiveMsToday)
        binding.heroBlocksValue.text = state.blocksToday.toString()

        bindMetrics(state)
        bindFocusProtection(state)
        bindCalendarSection(state)
        bindSleepSection(state)
    }

    private fun bindMetrics(state: DashboardState) {
        val healthNeeded = state.healthConnectAvailable && !state.healthPermissionGranted
        binding.metricSteps.bind(
            icon = R.drawable.ic_footprints,
            value = DashboardFormatter.formatSteps(state.stepsToday),
            label = context.getString(R.string.metric_steps),
            available = state.hasStepData && !healthNeeded,
        )
        binding.metricScreenTime.bind(
            icon = R.drawable.ic_screen_time,
            value = DashboardFormatter.formatDurationMs(state.unproductiveMsToday),
            label = context.getString(R.string.metric_screen_time),
            available = true,
        )
        binding.metricBlocks.bind(
            icon = R.drawable.ic_shield,
            value = state.blocksToday.toString(),
            label = context.getString(R.string.metric_blocks),
            available = true,
        )
        binding.metricSleep.bind(
            icon = R.drawable.ic_moon,
            value = state.sleepDurationMs?.let { DashboardFormatter.formatDurationMs(it) }.orEmpty(),
            label = context.getString(R.string.metric_sleep),
            available = state.hasSleepData && !healthNeeded,
        )
    }

    private fun bindFocusProtection(state: DashboardState) {
        val fullyOn = state.serviceEnabled && state.blockingEnabled
        binding.focusProtectionCard.bind(
            title = context.getString(R.string.focus_protection_title),
            body = if (fullyOn) {
                context.getString(R.string.focus_protection_on_body)
            } else {
                context.getString(R.string.focus_protection_off_body)
            },
            enabled = fullyOn,
            switchChecked = state.blockingEnabled,
            switchLabel = context.getString(R.string.toggle_blocking),
            warning = if (!state.serviceEnabled) {
                context.getString(R.string.focus_protection_service_off)
            } else {
                null
            },
            actionLabel = if (!state.serviceEnabled) {
                context.getString(R.string.open_accessibility_settings)
            } else {
                null
            },
            onCheckedChange = onBlockingToggled,
            onAction = onOpenAccessibilitySettings,
        )
    }

    private fun bindCalendarSection(state: DashboardState) {
        binding.calendarHeader.bind(context.getString(R.string.schedule_title))
        binding.calendarList.removeAllViews()

        if (!state.calendarPermissionGranted) {
            binding.calendarList.visibility = View.GONE
            binding.calendarOverflowText.visibility = View.GONE
            binding.calendarEmpty.visibility = View.VISIBLE
            binding.calendarEmpty.bind(
                message = context.getString(R.string.dashboard_calendar_permission),
                actionLabel = context.getString(R.string.request_calendar_permission),
                onAction = onRequestCalendarPermission,
                icon = R.drawable.ic_calendar,
            )
            return
        }

        if (state.calendarEvents.isEmpty()) {
            binding.calendarList.visibility = View.GONE
            binding.calendarOverflowText.visibility = View.GONE
            binding.calendarEmpty.visibility = View.VISIBLE
            binding.calendarEmpty.bind(
                message = context.getString(R.string.schedule_clear),
                icon = R.drawable.ic_calendar,
            )
            return
        }

        binding.calendarEmpty.visibility = View.GONE
        binding.calendarList.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(context)
        state.calendarEvents.forEach { event ->
            val rowBinding = ItemCalendarRowBinding.inflate(inflater, binding.calendarList, false)
            rowBinding.eventTimeText.text = DashboardFormatter.formatCalendarTime(event)
            rowBinding.eventTitleText.text = event.title
            if (event.location.isNullOrBlank()) {
                rowBinding.eventLocationText.visibility = View.GONE
            } else {
                rowBinding.eventLocationText.visibility = View.VISIBLE
                rowBinding.eventLocationText.text = event.location
            }
            binding.calendarList.addView(rowBinding.root)
        }

        if (state.calendarOverflowCount > 0) {
            binding.calendarOverflowText.visibility = View.VISIBLE
            binding.calendarOverflowText.text = context.getString(
                R.string.dashboard_calendar_overflow,
                state.calendarOverflowCount,
            )
        } else {
            binding.calendarOverflowText.visibility = View.GONE
        }
    }

    private fun bindSleepSection(state: DashboardState) {
        val showPermissionCta = state.healthConnectAvailable && !state.healthPermissionGranted
        if (showPermissionCta) {
            binding.sleepDurationText.visibility = View.GONE
            binding.sleepRangeText.visibility = View.GONE
            binding.sleepEmpty.visibility = View.VISIBLE
            binding.sleepEmpty.bind(
                message = context.getString(R.string.dashboard_sleep_permission),
                actionLabel = context.getString(R.string.request_health_permissions),
                onAction = onRequestHealthPermissions,
                icon = R.drawable.ic_moon,
            )
            return
        }

        if (state.hasSleepData && state.sleepDurationMs != null) {
            binding.sleepDurationText.visibility = View.VISIBLE
            binding.sleepRangeText.visibility = View.VISIBLE
            binding.sleepEmpty.visibility = View.GONE
            binding.sleepDurationText.text = DashboardFormatter.formatDurationMs(state.sleepDurationMs)
            binding.sleepRangeText.text = context.getString(
                R.string.sleep_range,
                state.sleepBedtime.orEmpty(),
                state.sleepWake.orEmpty(),
            )
        } else {
            binding.sleepDurationText.visibility = View.GONE
            binding.sleepRangeText.visibility = View.GONE
            binding.sleepEmpty.visibility = View.VISIBLE
            binding.sleepEmpty.bind(
                message = context.getString(R.string.dashboard_sleep_empty),
                icon = R.drawable.ic_moon,
            )
        }
    }
}
