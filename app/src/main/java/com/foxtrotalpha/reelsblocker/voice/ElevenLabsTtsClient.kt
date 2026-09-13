package com.foxtrotalpha.reelsblocker.voice

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

internal class ElevenLabsTtsClient(
    private val http: OkHttpClient = GeminiRoastClient.defaultClient,
) {
    fun synthesizeToFile(text: String, output: File) {
        val body = JSONObject()
            .put("text", text)
            .put("model_id", MODEL_ID)
            .put(
                "voice_settings",
                JSONObject()
                    .put("stability", 0.35)
                    .put("similarity_boost", 0.75)
                    .put("style", 0.0)
                    .put("use_speaker_boost", true)
                    .put("speed", 1.05),
            )

        val request = Request.Builder()
            .url(
                "https://api.elevenlabs.io/v1/text-to-speech/${ApiKeys.elevenLabsVoiceId}" +
                    "?output_format=mp3_44100_128",
            )
            .addHeader("xi-api-key", ApiKeys.elevenLabs)
            .addHeader("Accept", "audio/mpeg")
            .addHeader("Content-Type", JSON)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string().orEmpty().take(180)
                error("ElevenLabs HTTP ${response.code}: $detail")
            }
            val bytes = response.body?.bytes() ?: error("ElevenLabs returned empty audio")
            output.writeBytes(bytes)
        }
    }

    companion object {
        private const val JSON = "application/json"
        private val JSON_MEDIA = JSON.toMediaType()
        private const val MODEL_ID = "eleven_flash_v2_5"
    }
}
