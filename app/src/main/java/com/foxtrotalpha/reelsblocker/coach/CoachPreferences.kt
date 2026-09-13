package com.foxtrotalpha.reelsblocker.coach

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal data class CoachChatMessage(
    val role: String,
    val text: String,
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

internal object CoachPreferences {
    private const val PREFS_NAME = "coach_prefs"
    private const val KEY_THREAD_ID = "thread_id"
    private const val KEY_ASSISTANT_ID = "assistant_id"
    private const val KEY_ASSISTANT_SETUP = "assistant_setup_version"
    private const val KEY_MESSAGES = "messages"
    const val ASSISTANT_SETUP_VERSION = 3

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun threadId(context: Context): String? {
        return prefs(context).getString(KEY_THREAD_ID, null)?.ifBlank { null }
    }

    fun assistantId(context: Context): String? {
        return prefs(context).getString(KEY_ASSISTANT_ID, null)?.ifBlank { null }
    }

    fun assistantSetupVersion(context: Context): Int {
        return prefs(context).getInt(KEY_ASSISTANT_SETUP, 0)
    }

    fun saveSession(context: Context, threadId: String?, assistantId: String?) {
        prefs(context).edit()
            .putString(KEY_THREAD_ID, threadId.orEmpty())
            .putString(KEY_ASSISTANT_ID, assistantId.orEmpty())
            .apply()
    }

    fun saveAssistant(context: Context, assistantId: String) {
        prefs(context).edit()
            .putString(KEY_ASSISTANT_ID, assistantId)
            .putInt(KEY_ASSISTANT_SETUP, ASSISTANT_SETUP_VERSION)
            .apply()
    }

    fun loadMessages(context: Context): List<CoachChatMessage> {
        val raw = prefs(context).getString(KEY_MESSAGES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val role = item.optString("role")
                    val text = item.optString("text")
                    if (role.isNotBlank() && text.isNotBlank()) {
                        add(CoachChatMessage(role, text))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveMessages(context: Context, messages: List<CoachChatMessage>) {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(
                JSONObject()
                    .put("role", message.role)
                    .put("text", message.text),
            )
        }
        prefs(context).edit().putString(KEY_MESSAGES, array.toString()).apply()
    }

    fun startNewChat(context: Context) {
        prefs(context).edit()
            .remove(KEY_THREAD_ID)
            .remove(KEY_MESSAGES)
            .apply()
    }
}
