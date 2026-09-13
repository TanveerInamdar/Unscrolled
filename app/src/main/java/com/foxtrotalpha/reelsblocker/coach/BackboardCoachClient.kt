package com.foxtrotalpha.reelsblocker.coach

import com.foxtrotalpha.reelsblocker.voice.ApiKeys
import com.foxtrotalpha.reelsblocker.voice.GeminiRoastClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal data class BackboardMessageResponse(
    val content: String,
    val threadId: String?,
    val assistantId: String?,
    val status: String,
)

internal class BackboardCoachClient(
    private val http: OkHttpClient = client,
) {
    suspend fun ensureAssistant(existingId: String?): String {
        if (!existingId.isNullOrBlank()) {
            updateAssistant(existingId)
            return existingId
        }
        return createAssistant()
    }

    suspend fun sendMessage(
        content: String,
        threadId: String? = null,
        assistantId: String? = null,
        systemPrompt: String? = null,
    ): BackboardMessageResponse = withContext(Dispatchers.IO) {
        val key = requireKey()

        val body = JSONObject()
            .put("content", content)
            .put("stream", false)
            .put("memory", "Auto")
            .put("llm_provider", LLM_PROVIDER)
            .put("model_name", MODEL_NAME)
        if (!threadId.isNullOrBlank()) {
            body.put("thread_id", threadId)
        }
        if (!assistantId.isNullOrBlank()) {
            body.put("assistant_id", assistantId)
        }
        if (!systemPrompt.isNullOrBlank()) {
            body.put("system_prompt", systemPrompt)
        }

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("X-API-Key", key)
            .addHeader("Content-Type", JSON)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Backboard HTTP ${response.code}: ${payload.take(180)}")
            }
            parse(payload)
        }
    }

    private suspend fun createAssistant(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$ASSISTANTS_URL")
            .addHeader("X-API-Key", requireKey())
            .addHeader("Content-Type", JSON)
            .post(assistantBody().toString().toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Backboard create assistant HTTP ${response.code}: ${payload.take(180)}")
            }
            JSONObject(payload).optString("assistant_id").ifBlank {
                error("Backboard create assistant returned no assistant_id")
            }
        }
    }

    private suspend fun updateAssistant(assistantId: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$ASSISTANTS_URL/$assistantId")
            .addHeader("X-API-Key", requireKey())
            .addHeader("Content-Type", JSON)
            .put(assistantBody().toString().toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Backboard update assistant HTTP ${response.code}: ${payload.take(180)}")
            }
        }
    }

    private fun assistantBody(): JSONObject {
        return JSONObject()
            .put("name", "Foxtrot Coach")
            .put("system_prompt", CoachSnapshotBuilder.PERSONA)
            .put("custom_fact_extraction_prompt", CoachSnapshotBuilder.FACT_EXTRACTION_PROMPT)
            .put("custom_update_memory_prompt", CoachSnapshotBuilder.MEMORY_UPDATE_PROMPT)
    }

    private fun requireKey(): String {
        val key = ApiKeys.backboard
        if (key.isEmpty()) {
            error("Backboard API key is not configured")
        }
        return key
    }

    private fun parse(payload: String): BackboardMessageResponse {
        val root = JSONObject(payload)
        val status = root.optString("status").ifBlank { "COMPLETED" }
        if (status.equals("REQUIRES_ACTION", ignoreCase = true)) {
            error("Backboard requested a tool call; this coach has no tools")
        }
        val text = root.optString("content").ifBlank {
            root.optJSONObject("message")?.optString("content").orEmpty()
        }.trim()
        if (text.isBlank()) {
            error("Backboard returned an empty reply")
        }
        return BackboardMessageResponse(
            content = text,
            threadId = root.optString("thread_id").ifBlank { null },
            assistantId = root.optString("assistant_id").ifBlank { null },
            status = status,
        )
    }

    companion object {
        private const val JSON = "application/json"
        private val JSON_MEDIA = JSON.toMediaType()
        private const val ENDPOINT = "https://app.backboard.io/api/threads/messages"
        private const val ASSISTANTS_URL = "https://app.backboard.io/api/assistants"
        private const val LLM_PROVIDER = "openrouter"
        private const val MODEL_NAME = "minimax/minimax-m3"

        private val client: OkHttpClient = GeminiRoastClient.defaultClient.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
