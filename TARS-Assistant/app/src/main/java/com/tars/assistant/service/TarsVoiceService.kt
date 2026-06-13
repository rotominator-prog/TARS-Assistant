package com.tars.assistant.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.tars.assistant.model.VoiceGender
import com.tars.assistant.model.VoicePreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

/**
 * TARS Voice Service — TTS optimizat și rafinat
 *
 * Profil vocal TARS:
 * - Pitch scăzut (0.72f) — voce gravă, autoritar
 * - Viteză deliberată (0.82f) — fiecare cuvânt contează
 * - Pauze dramatice între propoziții
 * - Bass boost subtil pentru profunzime
 * - Selecție automată a celei mai bune voci masculine disponibile
 * - Queue inteligent — nu taie propoziția curentă
 */
class TarsVoiceService(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    // TARS vocal profile
    private var pitch      = 0.72f   // grav, robotic
    private var speechRate = 0.82f   // deliberat, precis
    private var volume     = 1.0f
    private var preferredGender = VoiceGender.MALE   // implicit: voce masculină (ca TARS)
    private var preset = VoicePreset.TARS
    // Limba/accentul preferat pt. selecția vocii: TARS=ro/us, JARVIS=en-GB.
    private var preferBritish = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private val _ttsAvailable = MutableStateFlow(false)
    val ttsAvailable: StateFlow<Boolean> = _ttsAvailable

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAcceptsDelayedFocusGain(false)
        .build()

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            _ttsAvailable.value = false
            return
        }

        // 1. Limbă — depinde de preset. JARVIS preferă engleza britanică;
        //    TARS preferă româna cu fallback englez.
        applyLanguageForPreset()

        // 2. Selectează cea mai bună voce pe gen/accent
        selectTarsVoice()

        // 3. Aplică profilul vocal TARS
        tts?.setPitch(pitch)
        tts?.setSpeechRate(speechRate)

        // 4. Configurează audio effects pe session-ul TTS

        // 5. Progress listener
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }
            override fun onDone(utteranceId: String?) {
                // Verifică dacă mai sunt chunk-uri în coadă
                if (utteranceId?.endsWith("_LAST") == true) {
                    _isSpeaking.value = false
                    abandonFocus()
                }
            }
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                abandonFocus()
            }
        })

        isInitialized = true
        _ttsAvailable.value = true
    }

    private fun applyLanguageForPreset() {
        if (preferBritish) {
            // JARVIS: engleză britanică, cu fallback la US apoi RO.
            val gb = tts?.setLanguage(Locale.UK)
            if (gb == TextToSpeech.LANG_MISSING_DATA || gb == TextToSpeech.LANG_NOT_SUPPORTED) {
                val us = tts?.setLanguage(Locale.US)
                if (us == TextToSpeech.LANG_MISSING_DATA || us == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale("ro", "RO"))
                }
            }
        } else {
            // TARS: română cu fallback englez.
            val ro = tts?.setLanguage(Locale("ro", "RO"))
            if (ro == TextToSpeech.LANG_MISSING_DATA || ro == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
        }
    }

    /**
     * Selectează vocea optimă, respectând genul preferat și — pentru JARVIS —
     * accentul britanic. Scoring: limbă/accent, local (fără rețea), gen, calitate.
     */
    private fun selectTarsVoice() {
        val voices = tts?.voices?.toList() ?: return

        val scored = voices
            .filter {
                if (preferBritish) it.locale.language == "en"
                else it.locale.language in listOf("ro", "en")
            }
            .map { voice ->
                var score = 0
                val country = voice.locale.country.uppercase()

                if (preferBritish) {
                    if (country == "GB" || country == "UK") score += 14  // accent britanic = JARVIS
                    if (voice.locale.language == "en") score += 4
                } else {
                    if (voice.locale.language == "ro") score += 10
                }

                if (!voice.isNetworkConnectionRequired) score += 5

                val name = voice.name.lowercase()
                val looksMale = isMaleVoiceName(name)
                val looksFemale = isFemaleVoiceName(name)

                when (preferredGender) {
                    VoiceGender.MALE -> {
                        if (looksMale) score += 12
                        if (looksFemale) score -= 8
                        if (any(name, "deep", "low", "bass", "adam", "daniel", "george")) score += 4
                    }
                    VoiceGender.FEMALE -> {
                        if (looksFemale) score += 12
                        if (looksMale) score -= 8
                    }
                    VoiceGender.AUTO -> {
                        if (looksMale) score += 4
                    }
                }

                if (voice.quality >= Voice.QUALITY_NORMAL) score += 3
                Pair(score, voice)
            }
            .sortedByDescending { it.first }

        scored.firstOrNull()?.second?.let { tts?.voice = it }
    }

    // Euristici de gen pe baza numelui vocii (Android nu expune genul direct).
    private fun isMaleVoiceName(name: String): Boolean {
        if ("female" in name || "fem" in name) return false
        if ("male" in name) return true
        return any(name, "male", "man", "adam", "daniel", "george", "marius",
            "andrei", "#male", "-male")
    }

    private fun isFemaleVoiceName(name: String): Boolean =
        any(name, "female", "woman", "ioana", "elena", "maria", "#female", "-female")

    /** Schimbă genul vocii și re-selectează imediat. */
    fun setVoiceGender(gender: VoiceGender) {
        preferredGender = gender
        if (isInitialized) selectTarsVoice()
    }

    fun getVoiceGender() = preferredGender

    /**
     * Aplică un preset complet de voce: pitch, viteză, gen, accent.
     * TARS = grav/robotic/deliberat. JARVIS = britanic, mai elegant și fluid.
     */
    fun applyPreset(p: VoicePreset) {
        preset = p
        when (p) {
            VoicePreset.TARS -> {
                preferBritish = false
                preferredGender = VoiceGender.MALE
                pitch = 0.72f
                speechRate = 0.82f
            }
            VoicePreset.JARVIS -> {
                preferBritish = true              // engleză britanică
                preferredGender = VoiceGender.MALE
                pitch = 0.92f                     // mai puțin grav, mai elegant
                speechRate = 0.96f                // mai fluid, mai puțin "mecanic"
            }
            VoicePreset.CUSTOM -> { /* păstrează valorile curente */ }
        }
        if (isInitialized) {
            applyLanguageForPreset()
            selectTarsVoice()
            tts?.setPitch(pitch)
            tts?.setSpeechRate(speechRate)
        }
    }

    fun getPreset() = preset

    private fun any(text: String, vararg keywords: String) = keywords.any { it in text }

    /**
     * Aplică efecte audio pentru a da profunzime și caracter vocii:
     */

    /**
     * Funcția principală de speech cu procesare avansată.
     * Împarte textul în chunk-uri cu pauze dramatice între ele.
     */
    fun speak(text: String) {
        if (!isInitialized) return
        tts?.stop() // Oprește orice vorbire curentă

        requestFocus()

        val chunks = buildSpeechQueue(text)
        if (chunks.isEmpty()) return

        chunks.forEachIndexed { index, chunk ->
            val isLast = index == chunks.size - 1
            val uid = "TARS_${System.currentTimeMillis()}_${index}${if (isLast) "_LAST" else ""}"
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uid)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
            }

            when (chunk) {
                is SpeechChunk.Text    -> tts?.speak(chunk.text, queueMode, params, uid)
                is SpeechChunk.Silence -> tts?.playSilentUtterance(chunk.ms, queueMode, uid)
            }
        }
    }

    /**
     * Construiește coada de speech cu pauze calibrate.
     * TARS vorbește ca un sistem care calculează fiecare cuvânt.
     */
    private fun buildSpeechQueue(raw: String): List<SpeechChunk> {
        val cleaned = raw
            .replace(Regex("[*_`#>]"), "")
            .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
            .replace(Regex("\\n{2,}"), ". ")
            .replace("\n", " ")
            .replace(Regex("  +"), " ")
            .trim()

        if (cleaned.isBlank()) return emptyList()

        val result = mutableListOf<SpeechChunk>()

        // Split pe propoziții
        val sentences = cleaned.split(Regex("(?<=[.!?…])\\s+"))

        sentences.forEachIndexed { idx, raw_s ->
            val s = raw_s.trim()
            if (s.isBlank()) return@forEachIndexed

            // Split suplimentar pe virgule lungi (propoziție > 15 cuvinte)
            val words = s.split(" ")
            if (words.size > 15) {
                // Găsește virgula din mijloc și împarte
                val midPoint = s.indexOf(",", s.length / 3)
                if (midPoint > 0) {
                    result.add(SpeechChunk.Text(s.substring(0, midPoint + 1).trim()))
                    result.add(SpeechChunk.Silence(220)) // pauză la virgulă
                    result.add(SpeechChunk.Text(s.substring(midPoint + 1).trim()))
                } else {
                    result.add(SpeechChunk.Text(s))
                }
            } else {
                result.add(SpeechChunk.Text(s))
            }

            // Pauze dramatice după fiecare propoziție
            if (idx < sentences.size - 1) {
                val pause = when {
                    s.endsWith("...") || s.endsWith("…") -> 750L  // lasă să respire
                    s.endsWith("!") -> 450L
                    s.endsWith("?") -> 400L
                    s.endsWith(".") -> 300L
                    else -> 180L
                }
                result.add(SpeechChunk.Silence(pause))
            }
        }

        return result
    }

    private fun requestFocus() = audioManager.requestAudioFocus(focusRequest)
    private fun abandonFocus() = audioManager.abandonAudioFocusRequest(focusRequest)

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        abandonFocus()
    }

    fun setPitch(p: Float) {
        pitch = p.coerceIn(0.5f, 1.5f)
        tts?.setPitch(pitch)
    }

    fun setSpeed(s: Float) {
        speechRate = s.coerceIn(0.5f, 1.5f)
        tts?.setSpeechRate(speechRate)
    }

    fun setVolume(v: Float) { volume = v.coerceIn(0f, 1f) }

    fun getCurrentPitch() = pitch
    fun getCurrentSpeed() = speechRate

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        abandonFocus()
    }
}

sealed class SpeechChunk {
    data class Text(val text: String) : SpeechChunk()
    data class Silence(val ms: Long) : SpeechChunk()
}
