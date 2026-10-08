package com.jsf.app.medication_solution.service

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

object TtsHelper {
    private var tts: TextToSpeech? = null
    private var ready = false

    fun speak(context: Context, text: String) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    val taLocale = Locale("ta", "IN")
                    tts?.language = if (tts?.isLanguageAvailable(taLocale) == TextToSpeech.LANG_AVAILABLE)
                        taLocale else Locale("en", "IN")
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "med_tts")
                }
            }
        } else if (ready) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "med_tts")
        }
    }

    fun shutdown() {
        tts?.stop(); tts?.shutdown(); tts = null; ready = false
    }
}
