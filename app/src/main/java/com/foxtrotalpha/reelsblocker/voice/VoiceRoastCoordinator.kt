package com.foxtrotalpha.reelsblocker.voice

import android.content.Context
import android.util.Log
import com.foxtrotalpha.reelsblocker.BlockerPreferences
import com.foxtrotalpha.reelsblocker.detector.ReelsDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object VoiceRoastCoordinator {

    private const val TAG = "VoiceRoast"
    private const val PREFS = "voice_roast_prefs"
    private const val KEY_RECENT = "recent_lines"
    private const val MAX_RECENT = 3

    private val inFlight = AtomicBoolean(false)
    private val playMutex = Mutex()
    private val gemini = GeminiRoastClient()
    private val elevenLabs = ElevenLabsTtsClient()

    @Volatile
    private var player: VoiceRoastPlayer? = null

    suspend fun speakForBlock(
        context: Context,
        packageName: String,
        reason: ReelsDetector.Result.Reason?,
    ) {
        if (!BlockerPreferences.isVoiceRoastEnabled(context)) {
            Log.d(TAG, "Voice roast skipped: audio toggle is off")
            return
        }
        if (!ApiKeys.isConfigured) {
            Log.w(TAG, "Voice roast skipped: API keys are empty in this install")
            return
        }
        if (!inFlight.compareAndSet(false, true)) {
            Log.d(TAG, "Voice roast skipped: previous request still in flight")
            return
        }

        val appContext = context.applicationContext
        try {
            val recent = loadRecent(appContext)
            val roastContext = RoastContextBuilder.build(appContext, packageName, reason, recent)
            val line = withContext(Dispatchers.IO) {
                gemini.generateLine(roastContext)
            }
            rememberLine(appContext, line)
            Log.i(TAG, "Gemini line ready")

            val audioFile = File(appContext.cacheDir, "voice_roast.mp3")
            try {
                withContext(Dispatchers.IO) {
                    elevenLabs.synthesizeToFile(line, audioFile)
                }
                play(appContext) { it.playFile(audioFile) }
            } catch (ttsError: Exception) {
                Log.w(TAG, "ElevenLabs failed, using device TTS", ttsError)
                withContext(Dispatchers.IO) {
                    play(appContext) { it.speakFallback(line) }
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Voice roast skipped", error)
        } finally {
            inFlight.set(false)
        }
    }

    fun release() {
        player?.release()
        player = null
    }

    private suspend fun play(context: Context, block: (VoiceRoastPlayer) -> Unit) {
        withContext(Dispatchers.Main) {
            playMutex.withLock {
                val roastPlayer = player ?: VoiceRoastPlayer(context).also { player = it }
                roastPlayer.stop()
                block(roastPlayer)
            }
        }
    }

    private fun loadRecent(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RECENT, "")
            .orEmpty()
        if (raw.isBlank()) {
            return emptyList()
        }
        return raw.split('\u001f').filter { it.isNotBlank() }.takeLast(MAX_RECENT)
    }

    private fun rememberLine(context: Context, line: String) {
        val next = (loadRecent(context) + line).takeLast(MAX_RECENT)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENT, next.joinToString("\u001f"))
            .apply()
    }
}
