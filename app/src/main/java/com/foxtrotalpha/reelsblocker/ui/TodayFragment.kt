package com.foxtrotalpha.reelsblocker.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.foxtrotalpha.reelsblocker.AccessibilityUtils
import com.foxtrotalpha.reelsblocker.BlockerPreferences
import com.foxtrotalpha.reelsblocker.MainActivity
import com.foxtrotalpha.reelsblocker.R
import com.foxtrotalpha.reelsblocker.ReelsBlockerService
import com.foxtrotalpha.reelsblocker.databinding.FragmentTodayBinding
import com.foxtrotalpha.reelsblocker.settings.SettingsActivity
import kotlinx.coroutines.launch

class TodayFragment : Fragment() {

    private var _binding: FragmentTodayBinding? = null
    private val binding get() = _binding!!
    private val dashboardViewModel: DashboardViewModel by activityViewModels()
    private lateinit var dashboardBinder: DashboardBinder

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTodayBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val host = requireActivity() as MainActivity
        dashboardBinder = DashboardBinder(
            context = requireContext(),
            binding = binding,
            onRequestHealthPermissions = { host.requestHealthConnectPermissions() },
            onRequestCalendarPermission = { host.requestCalendarPermission() },
            onOpenAccessibilitySettings = {
                AccessibilityUtils.openAccessibilitySettings(requireContext())
            },
            onBlockingToggled = { enabled ->
                BlockerPreferences.setBlockingEnabled(requireContext(), enabled)
                pushFocusProtection(blockingEnabled = enabled)
            },
            onVoiceRoastToggled = { enabled ->
                BlockerPreferences.setVoiceRoastEnabled(requireContext(), enabled)
                pushFocusProtection(voiceRoastEnabled = enabled)
            },
            onProfanityToggled = { enabled ->
                BlockerPreferences.setProfanityEnabled(requireContext(), enabled)
                pushFocusProtection(profanityEnabled = enabled)
            },
        )

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        binding.coachTeaserCard.setOnClickListener {
            host.selectTab(R.id.navCoach)
        }
        binding.todayRefresh.setColorSchemeResources(R.color.zen_sage)
        binding.todayRefresh.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                host.syncAllSources()
                binding.todayRefresh.isRefreshing = false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dashboardViewModel.state.collect { state ->
                    dashboardBinder.bind(state)
                }
            }
        }
    }

    private fun pushFocusProtection(
        blockingEnabled: Boolean = BlockerPreferences.isBlockingEnabled(requireContext()),
        voiceRoastEnabled: Boolean = BlockerPreferences.isVoiceRoastEnabled(requireContext()),
        profanityEnabled: Boolean = BlockerPreferences.isProfanityEnabled(requireContext()),
    ) {
        dashboardViewModel.setFocusProtectionState(
            serviceEnabled = AccessibilityUtils.isServiceEnabled(
                requireContext(),
                ReelsBlockerService::class.java,
            ),
            blockingEnabled = blockingEnabled,
            voiceRoastEnabled = voiceRoastEnabled,
            profanityEnabled = profanityEnabled,
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
