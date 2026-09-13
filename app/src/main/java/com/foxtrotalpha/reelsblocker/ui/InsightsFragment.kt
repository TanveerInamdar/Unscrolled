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
import com.foxtrotalpha.reelsblocker.ui.chart.ChartPoint
import com.foxtrotalpha.reelsblocker.ui.chart.LineChartView
import com.foxtrotalpha.reelsblocker.ui.components.ChartCardView
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
            setSparkline(true)
        }
        screenChart = LineChartView(requireContext()).apply {
            lineColor = ContextCompat.getColor(requireContext(), R.color.zen_gold)
            setSparkline(true)
        }
        blocksChart = BarChartView(requireContext()).apply {
            barColor = ContextCompat.getColor(requireContext(), R.color.zen_sage_soft)
            setSparkline(true)
        }
        sleepChart = BarChartView(requireContext()).apply {
            barColor = ContextCompat.getColor(requireContext(), R.color.zen_sleep)
            setSparkline(true)
        }

        binding.screenChartCard.setTitle(getString(R.string.metric_screen_time))
        binding.screenChartCard.setChart(screenChart)
        binding.blocksChartCard.setTitle(getString(R.string.insights_blocks_title))
        binding.blocksChartCard.setChart(blocksChart)
        binding.stepsChartCard.setTitle(getString(R.string.dashboard_section_steps))
        binding.stepsChartCard.setChart(stepsChart)
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
                insightsViewModel.state.collect { bind(it) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        insightsViewModel.refreshForToday()
    }

    private fun bind(state: InsightsState) {
        val healthNeeded = state.healthConnectAvailable && !state.healthPermissionGranted

        bindTile(
            card = binding.screenChartCard,
            chart = { screenChart.setPoints(state.screenPoints.ifEmpty { zeroWeek() }) },
            value = DashboardFormatter.formatDurationMs(state.totalUnproductiveMs),
            subtitle = state.unproductiveDelta?.text,
            hasData = state.hasScreenData,
            healthNeeded = false,
        )
        bindTile(
            card = binding.blocksChartCard,
            chart = { blocksChart.setPoints(state.blockPoints.ifEmpty { zeroWeek() }) },
            value = state.totalBlocks.toString(),
            subtitle = state.blocksDelta?.text,
            hasData = state.hasBlockData,
            healthNeeded = false,
        )
        bindTile(
            card = binding.stepsChartCard,
            chart = { stepsChart.setPoints(state.stepPoints.ifEmpty { zeroWeek() }) },
            value = DashboardFormatter.formatSteps(state.averageSteps),
            subtitle = state.stepsDelta?.text,
            hasData = state.hasStepData,
            healthNeeded = healthNeeded,
        )
        bindTile(
            card = binding.sleepChartCard,
            chart = { sleepChart.setPoints(state.sleepPoints.ifEmpty { zeroWeek() }) },
            value = state.averageSleepMs?.let { DashboardFormatter.formatDurationMs(it) }
                ?: getString(R.string.metric_unavailable),
            subtitle = state.sleepDelta?.text,
            hasData = state.hasSleepData,
            healthNeeded = healthNeeded,
        )
        bindApps(state)
    }

    private fun bindTile(
        card: ChartCardView,
        chart: () -> Unit,
        value: String,
        subtitle: String?,
        hasData: Boolean,
        healthNeeded: Boolean,
    ) {
        if (healthNeeded) {
            card.showEmpty(getString(R.string.dashboard_steps_permission))
            chart()
            return
        }
        if (!hasData) {
            card.showEmpty(getString(R.string.insights_keep_using))
            chart()
            return
        }
        card.showContent()
        card.setHeadline(value)
        card.setSubtitle(subtitle)
        chart()
    }

    private fun zeroWeek(): List<ChartPoint> {
        return listOf("M", "T", "W", "T", "F", "S", "S").map { ChartPoint(it, 0f) }
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
