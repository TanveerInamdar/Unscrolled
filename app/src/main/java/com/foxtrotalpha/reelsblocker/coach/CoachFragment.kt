package com.foxtrotalpha.reelsblocker.coach

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.foxtrotalpha.reelsblocker.databinding.FragmentCoachBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class CoachFragment : Fragment() {

    private var _binding: FragmentCoachBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CoachViewModel by viewModels()
    private lateinit var adapter: CoachMessageAdapter
    private var lastFailedText: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCoachBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = CoachMessageAdapter(requireContext())
        binding.coachMessageList.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.coachMessageList.adapter = adapter

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { target, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            target.updatePadding(bottom = maxOf(ime.bottom - bars.bottom, 0))
            insets
        }

        binding.coachNewChatButton.setOnClickListener { viewModel.startNewChat() }
        binding.coachSendButton.setOnClickListener { submitInput() }
        binding.coachRetryButton.setOnClickListener {
            lastFailedText?.let { viewModel.send(it) }
        }
        binding.coachInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitInput()
                true
            } else {
                false
            }
        }

        binding.promptWeek.setOnClickListener { sendPrompt(binding.promptWeek.text.toString()) }
        binding.promptPlan.setOnClickListener { sendPrompt(binding.promptPlan.text.toString()) }
        binding.promptTime.setOnClickListener { sendPrompt(binding.promptTime.text.toString()) }
        binding.promptImprove.setOnClickListener { sendPrompt(binding.promptImprove.text.toString()) }
        binding.promptHealth.setOnClickListener { sendPrompt(binding.promptHealth.text.toString()) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun sendPrompt(text: String) {
        if (viewModel.state.value.sending) return
        viewModel.send(text)
    }

    private fun submitInput() {
        val text = binding.coachInput.text?.toString().orEmpty().trim()
        if (text.isEmpty() || viewModel.state.value.sending) {
            return
        }
        lastFailedText = text
        viewModel.send(text)
        binding.coachInput.text = null
    }

    private fun render(state: CoachUiState) {
        val showChat = state.keyConfigured
        binding.coachSetupText.visibility = if (showChat) View.GONE else View.VISIBLE
        binding.coachChatPane.visibility = if (showChat) View.VISIBLE else View.GONE
        binding.coachComposer.visibility = if (showChat) View.VISIBLE else View.GONE
        binding.coachNewChatButton.visibility = if (showChat) View.VISIBLE else View.GONE

        adapter.submit(state.messages)
        if (state.messages.isNotEmpty()) {
            binding.coachMessageList.scrollToPosition(state.messages.lastIndex)
        }

        val showEmpty = showChat && state.messages.isEmpty() && !state.sending
        binding.coachEmptyState.visibility = if (showEmpty) View.VISIBLE else View.GONE
        binding.coachTypingRow.visibility = if (state.sending) View.VISIBLE else View.GONE
        binding.coachSendButton.isEnabled = showChat && !state.sending
        binding.coachInput.isEnabled = showChat && !state.sending

        val error = state.error
        if (!error.isNullOrBlank()) {
            lastFailedText = lastFailedText ?: binding.coachInput.text?.toString()
            binding.coachErrorText.visibility = View.VISIBLE
            binding.coachErrorText.text = error
            binding.coachRetryButton.visibility = View.VISIBLE
            Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
            viewModel.consumeError()
        } else if (!state.sending && state.messages.isNotEmpty() && lastFailedText != null &&
            state.messages.last().role == CoachChatMessage.ROLE_ASSISTANT
        ) {
            binding.coachErrorText.visibility = View.GONE
            binding.coachRetryButton.visibility = View.GONE
            lastFailedText = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
