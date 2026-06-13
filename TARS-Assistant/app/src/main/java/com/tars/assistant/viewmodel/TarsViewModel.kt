package com.tars.assistant.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tars.assistant.model.*
import com.tars.assistant.service.AiService
import com.tars.assistant.service.SpeechRecognitionService
import com.tars.assistant.service.TarsVoiceService
import com.tars.assistant.utils.PhoneActions
import com.tars.assistant.utils.PhoneActionType
import com.tars.assistant.utils.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.tars.assistant.utils.MessageCache
import com.tars.assistant.utils.NetworkMonitor
import com.tars.assistant.utils.OfflineQueue
import com.tars.assistant.utils.ErrorHandler

class TarsViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext

    // Services
    private val aiService = AiService()
    private val networkMonitor = NetworkMonitor(context)
    private val voiceService = TarsVoiceService(context)
    private val speechService = SpeechRecognitionService(context)

    // UI State
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _tarsState = MutableStateFlow(TarsState())
    val tarsState: StateFlow<TarsState> = _tarsState

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText

    private val _isOnboarded = MutableStateFlow(SecureStorage.isOnboardingDone(context))
    val isOnboarded: StateFlow<Boolean> = _isOnboarded

    private val _voiceEnabled = MutableStateFlow(SecureStorage.isVoiceEnabled(context))
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled

    private val _partialSpeech = MutableStateFlow("")
    val partialSpeech: StateFlow<String> = _partialSpeech

    val isListening: StateFlow<Boolean> = speechService.isListening
    val isSpeaking: StateFlow<Boolean> = voiceService.isSpeaking

    init {
        val cached = MessageCache.load(context)
        if (cached.isNotEmpty()) _messages.value = cached

        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                if (online && OfflineQueue.hasMessages(context)) {
                    val queued = OfflineQueue.dequeueAll(context)
                    queued.forEach { q -> sendMessage(q.text) }
                }
            }
        }

        val humor = SecureStorage.getHumorLevel(context)
        val savedPitch = SecureStorage.getFloat(context, "voice_pitch", 0.78f)
        val savedSpeed = SecureStorage.getFloat(context, "voice_speed", 0.88f)
        voiceService.setPitch(savedPitch)
        voiceService.setSpeed(savedSpeed)
        _tarsState.value = _tarsState.value.copy(humorLevel = humor)

        speechService.onResult = { text ->
            _partialSpeech.value = ""
            _inputText.value = text
            sendMessage(text)
        }
        speechService.onPartialResult = { partial -> _partialSpeech.value = partial }
        speechService.onError = { error ->
            _partialSpeech.value = ""
            addSystemMessage(error)
        }

        if (SecureStorage.isOnboardingDone(context)) showWelcome()
    }

    private fun showWelcome() {
        val humor = _tarsState.value.humorLevel
        val welcomes = if (humor > 60) listOf(
            "Sistem TARS operațional. Umor calibrat la $humor%. Fie că ți-a plăcut sau nu, asta e.",
            "TARS activ. Toți neuronii funcționează. Cei artificiali, desigur.",
            "Online. Gata să-ți răspund la întrebări stupide cu răspunsuri inteligente."
        ) else listOf(
            "TARS operațional. Cum pot să asist?",
            "Sistem activ. Aștept instrucțiuni.",
            "TARS online. Ready."
        )

        val msg = ChatMessage(content = welcomes.random(), role = MessageRole.TARS)
        _messages.value = listOf(msg)
        if (_voiceEnabled.value) voiceService.speak(msg.content)
    }

    /**
     * Finalizează onboarding-ul. Primul provider (din lanțul implicit) primește
     * cheia introdusă în primul pas; restul se configurează din Setări.
     */
    fun completeOnboarding(provider: AiProvider, apiKey: String, userName: String) {
        SecureStorage.saveProviderKey(context, provider, apiKey)
        SecureStorage.setProviderEnabled(context, provider, true)
        SecureStorage.saveTarsName(context, userName)
        SecureStorage.setOnboardingDone(context)
        _isOnboarded.value = true
        showWelcome()
    }

    fun sendMessage(text: String = _inputText.value) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        if (_tarsState.value.isThinking) return

        val providers = SecureStorage.getProviderChain(context)
        if (providers.isEmpty()) {
            addSystemMessage("Niciun provider AI configurat. Adaugă o cheie API în Setări.")
            return
        }

        if (!networkMonitor.isOnline.value) {
            OfflineQueue.enqueue(context, trimmed)
            _messages.value = _messages.value + ChatMessage(
                content = "Fără internet. Mesajul tău e salvat — îl trimit automat când revii online.",
                role = MessageRole.TARS
            )
            return
        }

        val userMsg = ChatMessage(content = trimmed, role = MessageRole.USER)
        _messages.value = _messages.value + userMsg
        _inputText.value = ""

        val actionType = PhoneActions.detectAction(trimmed)
        val typingMsg = ChatMessage(content = "...", role = MessageRole.TARS, isTyping = true)
        _messages.value = _messages.value + typingMsg
        _tarsState.value = _tarsState.value.copy(isThinking = true, statusText = "PROCESARE")

        viewModelScope.launch(Dispatchers.IO) {
            val result = aiService.sendMessage(
                providers = providers,
                conversationHistory = _messages.value.filter { !it.isTyping },
                humorLevel = _tarsState.value.humorLevel,
                userName = SecureStorage.getTarsName(context)
            )

            _messages.value = _messages.value.filter { !it.isTyping }

            result.fold(
                onSuccess = { ai ->
                    val tarsMsg = ChatMessage(content = ai.text, role = MessageRole.TARS)
                    _messages.value = _messages.value + tarsMsg
                    _tarsState.value = _tarsState.value.copy(
                        isThinking = false,
                        statusText = "STANDBY",
                        activeProvider = ai.providerName
                    )
                    MessageCache.save(context, _messages.value)
                    updateWidget(ai.text)
                    if (_voiceEnabled.value) voiceService.speak(ai.text)
                    handlePhoneAction(actionType, trimmed)
                },
                onFailure = { error ->
                    val parsed = ErrorHandler.handle(error)
                    if (ErrorHandler.isOfflineError(error)) OfflineQueue.enqueue(context, trimmed)
                    _messages.value = _messages.value + ChatMessage(
                        content = parsed.userMessage, role = MessageRole.TARS
                    )
                    _tarsState.value = _tarsState.value.copy(isThinking = false, statusText = "EROARE")
                    viewModelScope.launch {
                        delay(3000)
                        _tarsState.value = _tarsState.value.copy(statusText = "STANDBY")
                    }
                }
            )
        }
    }

    private fun handlePhoneAction(actionType: PhoneActionType, originalText: String) {
        viewModelScope.launch(Dispatchers.Main) {
            delay(1500)
            when (actionType) {
                PhoneActionType.ALARM -> {
                    val time = PhoneActions.parseTimeFromText(originalText)
                    if (time != null) PhoneActions.setAlarm(context, time.first, time.second)
                }
                PhoneActionType.TIMER -> PhoneActions.setTimer(context, 300)
                PhoneActionType.WEATHER -> PhoneActions.openWebSearch(context, "vremea azi")
                PhoneActionType.NEWS -> PhoneActions.openWebSearch(context, "stiri romania azi")
                PhoneActionType.CALENDAR -> PhoneActions.addCalendarEvent(context, "Eveniment TARS")
                else -> {}
            }
        }
    }

    private fun addSystemMessage(text: String) {
        _messages.value = _messages.value + ChatMessage(content = text, role = MessageRole.SYSTEM)
    }

    fun setInputText(text: String) { _inputText.value = text }

    fun setHumorLevel(level: Int) {
        _tarsState.value = _tarsState.value.copy(humorLevel = level)
        SecureStorage.saveHumorLevel(context, level)
    }

    fun toggleVoice() {
        val newVal = !_voiceEnabled.value
        _voiceEnabled.value = newVal
        SecureStorage.saveVoiceEnabled(context, newVal)
        if (!newVal) voiceService.stop()
    }

    fun getVoicePitch() = voiceService.getCurrentPitch()
    fun getVoiceSpeed() = voiceService.getCurrentSpeed()

    fun setVoicePitch(pitch: Float) {
        voiceService.setPitch(pitch)
        SecureStorage.saveFloat(context, "voice_pitch", pitch)
    }

    fun setVoiceSpeed(speed: Float) {
        voiceService.setSpeed(speed)
        SecureStorage.saveFloat(context, "voice_speed", speed)
    }

    fun testVoice(text: String) { if (_voiceEnabled.value) voiceService.speak(text) }

    fun startListening() {
        voiceService.stop()
        speechService.startListening()
    }

    fun stopListening() {
        speechService.stopListening()
        _partialSpeech.value = ""
    }

    fun stopSpeaking() { voiceService.stop() }

    fun clearConversation() {
        _messages.value = emptyList()
        MessageCache.clear(context)
        OfflineQueue.dequeueAll(context)
        showWelcome()
    }

    fun resetOnboarding() {
        SecureStorage.clearAllProviders(context)
        _isOnboarded.value = false
        _messages.value = emptyList()
    }

    fun injectMessage(text: String) {
        _messages.value = _messages.value + ChatMessage(content = text, role = MessageRole.TARS)
    }

    private fun updateWidget(lastResponse: String) {
        com.tars.assistant.widget.TarsWidget.updateAllWidgets(
            context, status = "STANDBY", lastMsg = lastResponse
        )
    }

    override fun onCleared() {
        super.onCleared()
        voiceService.shutdown()
        speechService.destroy()
        networkMonitor.unregister()
    }
}
