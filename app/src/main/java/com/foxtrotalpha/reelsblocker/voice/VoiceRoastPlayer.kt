package com.foxtrotalpha.reelsblocker.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class VoiceRoastPlayer(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    fun playFile(file: File) {
        stopPlayback()
        requestFocus()
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(speechAttributes())
                setDataSource(file.absolutePath)
                setOnCompletionListener { mp ->
                    mp.release()
                    if (mediaPlayer === mp) {
                        mediaPlayer = null
                    }
                    releaseFocus()
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    if (mediaPlayer === mp) {
                        mediaPlayer = null
                    }
                    releaseFocus()
                    true
                }
            }
            mediaPlayer = player
            player.prepare()
            player.start()
        } catch (_: Exception) {
            mediaPlayer?.release()
            mediaPlayer = null
            releaseFocus()
            android.util.Log.w("VoiceRoast", "MediaPlayer failed to start")
        }
    }

    fun speakFallback(text: String) {
        val engine = ensureTts() ?: return
        stopPlayback()
        requestFocus()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                releaseFocus()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                releaseFocus()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                releaseFocus()
            }
        })
        val spoken = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        if (spoken != TextToSpeech.SUCCESS) {
            releaseFocus()
        }
    }

    fun stop() {
        stopPlayback()
        tts?.stop()
        releaseFocus()
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    private fun stopPlayback() {
        mediaPlayer?.let { player ->
            try {
                player.stop()
            } catch (_: IllegalStateException) {
                // Already released or not prepared.
            }
            player.release()
        }
        mediaPlayer = null
    }

    private fun ensureTts(): TextToSpeech? {
        if (ttsReady) {
            return tts
        }
        val latch = CountDownLatch(1)
        val engine = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            latch.countDown()
        }
        tts = engine
        latch.await(2, TimeUnit.SECONDS)
        if (!ttsReady) {
            engine.shutdown()
            tts = null
            return null
        }
        engine.language = Locale.US
        return engine
    }

    private fun speechAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }

    private fun requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(speechAttributes())
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            )
        }
    }

    private fun releaseFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    companion object {
        private const val UTTERANCE_ID = "voice_roast"
    }
}
