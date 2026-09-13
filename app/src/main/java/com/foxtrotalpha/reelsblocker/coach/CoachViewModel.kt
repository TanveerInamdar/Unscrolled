package com.foxtrotalpha.reelsblocker.coach

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foxtrotalpha.reelsblocker.voice.ApiKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class CoachUiState(
    val keyConfigured: Boolean = false,
    val messages: List<CoachChatMessage> = emptyList(),
    val sending: Boolean = false,
    val error: String? = null,
)

internal class CoachViewModel(application: Application) : AndroidViewModel(application) {

    private val client = BackboardCoachClient()

    private val _state = MutableStateFlow(
        CoachUiState(
            keyConfigured = ApiKeys.isBackboardConfigured,
            messages = CoachPreferences.loadMessages(application),
        ),
    )
    val state: StateFlow<CoachUiState> = _state.asStateFlow()

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    fun startNewChat() {
        val context = getApplication<Application>()
        CoachPreferences.startNewChat(context)
        _state.update {
            it.copy(messages = emptyList(), sending = false, error = null)
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.sending) {
            return
        }
        if (!ApiKeys.isBackboardConfigured) {
            _state.update { it.copy(error = "Add BACKBOARD_API_KEY to local.properties") }
            return
        }

        val context = getApplication<Application>()
        val userMessage = CoachChatMessage(CoachChatMessage.ROLE_USER, trimmed)
        val outgoing = _state.value.messages + userMessage
        _state.update { it.copy(messages = outgoing, sending = true, error = null) }
        CoachPreferences.saveMessages(context, outgoing)

        viewModelScope.launch {
            try {
                val threadId = CoachPreferences.threadId(context)
                var assistantId = CoachPreferences.assistantId(context)
                val needsAssistantSetup = assistantId == null ||
                    CoachPreferences.assistantSetupVersion(context) < CoachPreferences.ASSISTANT_SETUP_VERSION
                if (needsAssistantSetup) {
                    assistantId = client.ensureAssistant(assistantId)
                    CoachPreferences.saveAssistant(context, assistantId)
                }
                val systemPrompt = if (threadId == null) {
                    CoachSnapshotBuilder.buildSystemPrompt(context)
                } else {
                    null
                }
                val response = client.sendMessage(
                    content = trimmed,
                    threadId = threadId,
                    assistantId = assistantId,
                    systemPrompt = systemPrompt,
                )
                CoachPreferences.saveSession(
                    context,
                    threadId = response.threadId ?: threadId,
                    assistantId = response.assistantId ?: assistantId,
                )
                val complete = outgoing + CoachChatMessage(
                    CoachChatMessage.ROLE_ASSISTANT,
                    response.content,
                )
                CoachPreferences.saveMessages(context, complete)
                _state.update { it.copy(messages = complete, sending = false) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        sending = false,
                        error = error.message?.take(200) ?: "Coach request failed",
                    )
                }
            }
        }
    }
}
