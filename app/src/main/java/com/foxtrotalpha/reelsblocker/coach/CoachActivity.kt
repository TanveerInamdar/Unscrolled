package com.foxtrotalpha.reelsblocker.coach

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.foxtrotalpha.reelsblocker.databinding.ActivityCoachBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class CoachActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCoachBinding
    private val viewModel: CoachViewModel by viewModels()
    private lateinit var adapter: CoachMessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCoachBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = CoachMessageAdapter(this)
        binding.coachMessageList.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.coachMessageList.adapter = adapter

        binding.coachBackButton.setOnClickListener { finish() }
        binding.coachNewChatButton.setOnClickListener { viewModel.startNewChat() }
        binding.coachSendButton.setOnClickListener { submitInput() }
        binding.coachInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitInput()
                true
            } else {
                false
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun submitInput() {
        val text = binding.coachInput.text?.toString().orEmpty().trim()
        if (text.isEmpty() || viewModel.state.value.sending) {
            return
        }
        viewModel.send(text)
        binding.coachInput.text = null
    }

    private fun render(state: CoachUiState) {
        val showChat = state.keyConfigured
        binding.coachSetupText.visibility = if (showChat) View.GONE else View.VISIBLE
        binding.coachMessageList.visibility = if (showChat) View.VISIBLE else View.GONE
        binding.coachComposer.visibility = if (showChat) View.VISIBLE else View.GONE
        binding.coachNewChatButton.visibility = if (showChat) View.VISIBLE else View.GONE

        adapter.submit(state.messages)
        if (state.messages.isNotEmpty()) {
            binding.coachMessageList.scrollToPosition(state.messages.lastIndex)
        }

        val showEmpty = showChat && state.messages.isEmpty() && !state.sending
        binding.coachEmptyHint.visibility = if (showEmpty) View.VISIBLE else View.GONE
        binding.coachProgress.visibility = if (state.sending) View.VISIBLE else View.GONE
        binding.coachSendButton.isEnabled = showChat && !state.sending
        binding.coachInput.isEnabled = showChat && !state.sending

        state.error?.let { message ->
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
            viewModel.consumeError()
        }
    }
}
