package com.foxtrotalpha.reelsblocker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.foxtrotalpha.reelsblocker.MainActivity
import com.foxtrotalpha.reelsblocker.R
import com.foxtrotalpha.reelsblocker.databinding.FragmentInsightsBinding
import com.foxtrotalpha.reelsblocker.databinding.ItemAppUsageRowBinding
import com.foxtrotalpha.reelsblocker.ui.chart.BarChartView
import com.foxtrotalpha.reelsblocker.ui.chart.LineChartView
import kotlinx.coroutines.launch

class InsightsFragment : Fragment() {

    private var _binding: FragmentInsightsBinding? = null
    private val binding get() = _binding!!
    private val insightsViewModel: InsightsViewModel by viewModels()
    private val dashboardViewModel: DashboardViewModel by activityViewModels()

    private lateinit var stepsChart: BarChartView
    private lateinit var screenChart: LineChartView
    private lateinit var blocksChart: BarChartView
    private lateinit var sleepChart: BarChartView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentInsightsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val host = requireActivity() as MainActivity

        stepsChart = BarChartView(requireContext()).apply {
            barColor = ContextCompat.getColor(requireContext(), R.color.zen_sage)
        }
        screenChart = LineChartView(requireContext()).apply {
            lineColor = ContextCompat.getColor(requireContext(), R.color.zen_gold)
        }
        blocksChart = BarChartView(requireContext()).apply {
            barColor = ContextCompat.getColor(requireContext(), R.color.zen_sage_soft)
        }
        sleepChart = BarChartView(requireContext()).apply {
            barColor = ContextCompat.getColor(requireContext(), R.color.zen_sleep)
        }

        binding.stepsChartCard.setTitle(getString(R.string.dashboard_section_steps))
        binding.stepsChartCard.setChart(stepsChart)
        binding.screenChartCard.setTitle(getString(R.string.insights_screen_title))
        binding.screenChartCard.setChart(screenChart)
        binding.blocksChartCard.setTitle(getString(R.string.insights_blocks_title))
        binding.blocksChartCard.setChart(blocksChart)
        binding.sleepChartCard.setTitle(getString(R.string.sleep_title))
        binding.sleepChartCard.setChart(sleepChart)

        binding.insightsRefresh.setColorSchemeResources(R.color.zen_sage)
        binding.insightsRefresh.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                host.syncAllSources()
                insightsViewModel.refreshForToday()
                binding.insightsRefresh.isRefreshing = false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    dashboardViewModel.state.collect { dash ->
                        insightsViewModel.setHealthConnectAvailable(dash.healthConnectAvailable)
                        insightsViewModel.setHealthPermissionGranted(dash.healthPermissionGranted)
                    }
                }
                launch {
                    insightsViewModel.state.collect { bind(it, host) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        insightsViewModel.refreshForToday()
    }

    private fun bind(state: InsightsState, host: MainActivity) {
        val healthNeeded = state.healthConnectAvailable && !state.healthPermissionGranted
        binding.summaryScreen.bind(
            icon = R.drawable.ic_screen_time,
            value = DashboardFormatter.formatDurationMs(state.totalUnproductiveMs),
            label = getString(R.string.insights_summary_unproductive),
            comparison = state.unproductiveDelta?.text,
            available = true,
        )
        binding.summaryBlocks.bind(
            icon = R.drawable.ic_shield,
            value = state.totalBlocks.toString(),
            label = getString(R.string.insights_summary_blocks),
            comparison = state.blocksDelta?.text,
            available = true,
        )
        binding.summarySteps.bind(
            icon = R.drawable.ic_footprints,
            value = DashboardFormatter.formatSteps(state.averageSteps),
            label = getString(R.string.insights_summary_steps),
            comparison = state.stepsDelta?.text,
            available = state.averageSteps != null && !healthNeeded,
        )
        binding.summarySleep.bind(
            icon = R.drawable.ic_moon,
            value = state.averageSleepMs?.let { DashboardFormatter.formatDurationMs(it) }.orEmpty(),
            label = getString(R.string.insights_summary_sleep),
            comparison = state.sleepDelta?.text,
            available = state.averageSleepMs != null && !healthNeeded,
        )

        bindChart(
            card = binding.stepsChartCard,
            chart = { stepsChart.setPoints(state.stepPoints) },
            hasData = state.hasStepData,
            headline = state.averageSteps?.let { getString(R.string.insights_avg_steps, DashboardFormatter.formatSteps(it)) },
            healthNeeded = healthNeeded,
            host = host,
        )
        bindChart(
            card = binding.screenChartCard,
            chart = { screenChart.setPoints(state.screenPoints) },
            hasData = state.hasScreenData,
            headline = getString(
                R.string.insights_avg_screen,
                DashboardFormatter.formatDurationMs(state.totalUnproductiveMs / 7),
            ).takeIf { state.hasScreenData },
            healthNeeded = false,
            host = host,
        )
        bindChart(
            card = binding.blocksChartCard,
            chart = { blocksChart.setPoints(state.blockPoints) },
            hasData = state.hasBlockData,
            headline = getString(R.string.insights_total_blocks, state.totalBlocks).takeIf { state.hasBlockData },
            healthNeeded = false,
            host = host,
        )
        bindBlocksBreakdown(state)
        bindChart(
            card = binding.sleepChartCard,
            chart = { sleepChart.setPoints(state.sleepPoints) },
            hasData = state.hasSleepData,
            headline = state.averageSleepMs?.let {
                getString(R.string.insights_avg_sleep, DashboardFormatter.formatDurationMs(it))
            },
            healthNeeded = healthNeeded,
            host = host,
        )
        bindApps(state)
    }

    private fun bindChart(
        card: com.foxtrotalpha.reelsblocker.ui.components.ChartCardView,
        chart: () -> Unit,
        hasData: Boolean,
        headline: String?,
        healthNeeded: Boolean,
        host: MainActivity,
    ) {
        if (healthNeeded) {
            card.showEmpty(
                message = getString(R.string.dashboard_steps_permission),
                actionLabel = getString(R.string.request_health_permissions),
                onAction = { host.requestHealthConnectPermissions() },
            )
            return
        }
        if (!hasData) {
            card.showEmpty(getString(R.string.insights_keep_using))
            return
        }
        card.showContent()
        card.setHeadline(headline)
        chart()
    }

    private fun bindBlocksBreakdown(state: InsightsState) {
        val extra = binding.blocksChartCard.extraSlot()
        extra.removeAllViews()
        if (!state.hasBlockData || state.blocksByApp.isEmpty()) {
            extra.visibility = View.GONE
            return
        }
        extra.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(requireContext())
        state.blocksByApp.take(4).forEach { row ->
            val rowBinding = ItemAppUsageRowBinding.inflate(inflater, extra, false)
            rowBinding.appNameText.text = row.label
            rowBinding.appDurationText.text = row.totalMs.toInt().toString()
            rowBinding.appUsageFill.layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                row.fraction.coerceIn(0.08f, 1f),
            )
            extra.addView(rowBinding.root)
        }
    }

    private fun bindApps(state: InsightsState) {
        binding.appsList.removeAllViews()
        if (state.unproductiveApps.isEmpty()) {
            binding.appsList.visibility = View.GONE
            binding.appsEmpty.visibility = View.VISIBLE
            binding.appsEmpty.bind(getString(R.string.insights_keep_using))
            return
        }
        binding.appsEmpty.visibility = View.GONE
        binding.appsList.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(requireContext())
        state.unproductiveApps.forEach { row ->
            val rowBinding = ItemAppUsageRowBinding.inflate(inflater, binding.appsList, false)
            rowBinding.appNameText.text = row.label
            rowBinding.appDurationText.text = DashboardFormatter.formatDurationMs(row.totalMs)
            rowBinding.appUsageFill.layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                row.fraction.coerceIn(0.08f, 1f),
            )
            binding.appsList.addView(rowBinding.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
