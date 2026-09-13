package com.foxtrotalpha.reelsblocker.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.foxtrotalpha.reelsblocker.R
import com.foxtrotalpha.reelsblocker.databinding.ActivityMainBinding
import com.foxtrotalpha.reelsblocker.databinding.ItemCalendarRowBinding
import com.foxtrotalpha.reelsblocker.databinding.ItemScreenTimeRowBinding
import com.foxtrotalpha.reelsblocker.databinding.ItemWeekBarBinding

class DashboardBinder(
    private val context: Context,
    private val binding: ActivityMainBinding,
    private val onRequestHealthPermissions: () -> Unit,
    private val onRequestCalendarPermission: () -> Unit,
) {

    fun bind(state: DashboardState) {
        binding.dashboardDateText.text = state.todayDateLabel

        binding.heroStepsValue.text = DashboardFormatter.formatSteps(state.stepsToday)
        binding.heroUnproductiveValue.text = DashboardFormatter.formatDurationMs(state.unproductiveMsToday)
        binding.heroBlocksValue.text = if (state.blocksToday == 0) {
            context.getString(R.string.dashboard_blocks_zero)
        } else {
            context.resources.getQuantityString(
                R.plurals.dashboard_blocks_count,
                state.blocksToday,
                state.blocksToday,
            )
        }

        bindStepsSection(state)
        bindScreenTimeSection(state)
        bindSleepSection(state)
        bindCalendarSection(state)
    }

    private fun bindStepsSection(state: DashboardState) {
        val showPermissionCta = state.healthConnectAvailable && !state.healthPermissionGranted
        binding.stepsPermissionButton.visibility = if (showPermissionCta) View.VISIBLE else View.GONE
        binding.stepsPermissionButton.setOnClickListener { onRequestHealthPermissions() }

        if (showPermissionCta) {
            binding.stepsTodayValue.visibility = View.GONE
            binding.stepsCaloriesText.visibility = View.GONE
            binding.stepsEmptyText.visibility = View.VISIBLE
            binding.stepsEmptyText.text = context.getString(R.string.dashboard_steps_permission)
            binding.stepsWeekChart.visibility = View.GONE
            return
        }

        binding.stepsTodayValue.visibility = View.VISIBLE
        binding.stepsWeekChart.visibility = View.VISIBLE

        if (state.hasStepData && state.stepsToday != null) {
            binding.stepsTodayValue.text = DashboardFormatter.formatSteps(state.stepsToday)
            val calories = DashboardFormatter.formatActiveCalories(state.activeCaloriesToday)
            if (calories != null) {
                binding.stepsCaloriesText.visibility = View.VISIBLE
                binding.stepsCaloriesText.text = calories
            } else {
                binding.stepsCaloriesText.visibility = View.GONE
            }
            binding.stepsEmptyText.visibility = View.GONE
        } else {
            binding.stepsTodayValue.text = "—"
            binding.stepsCaloriesText.visibility = View.GONE
            binding.stepsEmptyText.visibility = View.VISIBLE
            binding.stepsEmptyText.text = context.getString(R.string.dashboard_steps_empty)
        }

        bindWeekChart(binding.stepsWeekChart, state.weekStepBars)
    }

    private fun bindScreenTimeSection(state: DashboardState) {
        binding.screenTimeUnproductiveTotal.text = context.getString(
            R.string.dashboard_unproductive_total,
            DashboardFormatter.formatDurationMs(state.unproductiveMsToday),
        )

        binding.screenTimeList.removeAllViews()
        if (state.topAppsToday.isEmpty()) {
            binding.screenTimeEmptyText.visibility = View.VISIBLE
            binding.screenTimeEmptyText.text = context.getString(R.string.dashboard_screen_time_empty)
        } else {
            binding.screenTimeEmptyText.visibility = View.GONE
            val inflater = LayoutInflater.from(context)
            state.topAppsToday.forEach { row ->
                val rowBinding = ItemScreenTimeRowBinding.inflate(inflater, binding.screenTimeList, false)
                rowBinding.appNameText.text = row.label
                rowBinding.appDurationText.text = DashboardFormatter.formatDurationMs(row.foregroundMs)
                binding.screenTimeList.addView(rowBinding.root)
            }
        }

        bindWeekChart(binding.screenTimeWeekChart, state.weekUnproductiveBars)
    }

    private fun bindSleepSection(state: DashboardState) {
        val showPermissionCta = state.healthConnectAvailable && !state.healthPermissionGranted
        if (showPermissionCta) {
            binding.sleepSummaryText.visibility = View.GONE
            binding.sleepEmptyText.visibility = View.VISIBLE
            binding.sleepEmptyText.text = context.getString(R.string.dashboard_sleep_permission)
            return
        }

        val summary = state.sleepSummary
        if (summary != null) {
            binding.sleepSummaryText.visibility = View.VISIBLE
            binding.sleepSummaryText.text = summary
            binding.sleepEmptyText.visibility = View.GONE
        } else {
            binding.sleepSummaryText.visibility = View.GONE
            binding.sleepEmptyText.visibility = View.VISIBLE
            binding.sleepEmptyText.text = context.getString(R.string.dashboard_sleep_empty)
        }
    }

    private fun bindCalendarSection(state: DashboardState) {
        if (!state.calendarPermissionGranted) {
            binding.calendarPermissionButton.visibility = View.VISIBLE
            binding.calendarPermissionButton.setOnClickListener { onRequestCalendarPermission() }
            binding.calendarList.visibility = View.GONE
            binding.calendarEmptyText.visibility = View.VISIBLE
            binding.calendarEmptyText.text = context.getString(R.string.dashboard_calendar_permission)
            binding.calendarOverflowText.visibility = View.GONE
            return
        }

        binding.calendarPermissionButton.visibility = View.GONE
        binding.calendarList.removeAllViews()

        if (state.calendarEvents.isEmpty()) {
            binding.calendarList.visibility = View.GONE
            binding.calendarEmptyText.visibility = View.VISIBLE
            binding.calendarEmptyText.text = context.getString(R.string.dashboard_calendar_empty)
            binding.calendarOverflowText.visibility = View.GONE
            return
        }

        binding.calendarList.visibility = View.VISIBLE
        binding.calendarEmptyText.visibility = View.GONE
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

    private fun bindWeekChart(container: LinearLayout, bars: List<WeekBar>) {
        container.removeAllViews()
        if (bars.isEmpty()) return

        val maxValue = bars.maxOf { it.value }.coerceAtLeast(1L)
        val inflater = LayoutInflater.from(context)
        val barMaxHeightDp = 72

        bars.forEach { bar ->
            val barBinding = ItemWeekBarBinding.inflate(inflater, container, false)
            barBinding.dayLabelText.text = bar.label

            val heightDp = if (bar.value <= 0L) {
                4
            } else {
                ((bar.value.toFloat() / maxValue) * barMaxHeightDp).toInt().coerceAtLeast(4)
            }
            val density = context.resources.displayMetrics.density
            barBinding.barView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (heightDp * density).toInt(),
            ).apply {
                topMargin = (4 * density).toInt()
            }

            val barColor = if (bar.isToday) {
                R.color.primary
            } else {
                R.color.dashboard_bar_inactive
            }
            barBinding.barView.setBackgroundColor(ContextCompat.getColor(context, barColor))

            val labelColor = if (bar.isToday) {
                R.color.primary
            } else {
                R.color.dashboard_text_secondary
            }
            barBinding.dayLabelText.setTextColor(ContextCompat.getColor(context, labelColor))

            container.addView(barBinding.root)
        }
    }
}
