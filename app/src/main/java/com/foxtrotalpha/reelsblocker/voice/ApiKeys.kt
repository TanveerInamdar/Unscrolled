package com.foxtrotalpha.reelsblocker.voice

import com.foxtrotalpha.reelsblocker.BuildConfig

internal object ApiKeys {
    val gemini: String get() = BuildConfig.GEMINI_API_KEY.trim()
    val elevenLabs: String get() = BuildConfig.ELEVENLABS_API_KEY.trim()
    val elevenLabsVoiceId: String get() = BuildConfig.ELEVENLABS_VOICE_ID.trim()

    val isConfigured: Boolean
        get() = gemini.isNotEmpty() && elevenLabs.isNotEmpty() && elevenLabsVoiceId.isNotEmpty()
}
