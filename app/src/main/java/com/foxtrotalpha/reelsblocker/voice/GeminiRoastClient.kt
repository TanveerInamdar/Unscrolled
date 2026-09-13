package com.foxtrotalpha.reelsblocker.voice

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal class GeminiRoastClient(
    private val http: OkHttpClient = defaultClient,
) {
        fun generateLine(context: RoastContext): String {
        val body = JSONObject()
            .put(
                "system_instruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)),
                ),
            )
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", context.toUserPrompt())),
                    ),
                ),
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 1.05)
                    .put("maxOutputTokens", 40),
            )

        val request = Request.Builder()
            .url("$ENDPOINT?key=${ApiKeys.gemini}")
            .addHeader("Content-Type", JSON)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Gemini HTTP ${response.code}: ${payload.take(180)}")
            }
            return sanitizeLine(parseText(payload))
        }
    }

    private fun parseText(payload: String): String {
        val root = JSONObject(payload)
        val parts = root
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
        val text = buildString {
            for (index in 0 until parts.length()) {
                append(parts.getJSONObject(index).optString("text"))
            }
        }
        return text.trim()
    }

    private fun sanitizeLine(raw: String): String {
        val first = raw.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        return first
            .trim()
            .trim('"', '\'', '`', '“', '”')
            .replace(Regex("\\s+"), " ")
            .take(120)
            .ifBlank { error("Gemini returned an empty line") }
    }

    companion object {
        private const val JSON = "application/json"
        private val JSON_MEDIA = JSON.toMediaType()
        private const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent"

        private const val SYSTEM_PROMPT =
            "You write one spoken line for a phone that just blocked a short-form video. " +
                "Slightly rude, curt, quirky, a bit mean, and funny. Maximum twelve words. " +
                "No quotes, no emoji, no hashtags. Write numbers as words. Output only the line. " +
                "Use only the assigned roast angle. Unless the angle is step count, " +
                "do not mention steps, walking, or grass."

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
