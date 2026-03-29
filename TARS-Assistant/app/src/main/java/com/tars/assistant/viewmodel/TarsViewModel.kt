package com.tars.assistant.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tars.assistant.model.*
import com.tars.assistant.service.ClaudeService
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
    private val claudeService = ClaudeService()
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
        // Load persisted message history
        val cached = MessageCache.load(context)
        if (cached.isNotEmpty()) _messages.value = cached

        // Watch for network restore → flush offline queue
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                if (online && OfflineQueue.hasMessages(context)) {
                    val queued = OfflineQueue.dequeueAll(context)
                    queued.forEach { q -> sendMessage(q.text) }
                }
            }
        }

        // Load humor level
        val humor = SecureStorage.getHumorLevel(context)

        // Restore saved voice profile
        val savedPitch = SecureStorage.getFloat(context, "voice_pitch", 0.78f)
        val savedSpeed = SecureStorage.getFloat(context, "voice_speed", 0.88f)
        voiceService.setPitch(savedPitch)
        voiceService.setSpeed(savedSpeed)
        _tarsState.value = _tarsState.value.copy(humorLevel = humor)

        // Setup speech recognition callbacks
        speechService.onResult = { text ->
            _partialSpeech.value = ""
            _inputText.value = text
            sendMessage(text)
        }
        speechService.onPartialResult = { partial ->
            _partialSpeech.value = partial
        }
        speechService.onError = { error ->
            _partialSpeech.value = ""
            addSystemMessage(error)
        }

        // Show welcome if onboarded
        if (SecureStorage.isOnboardingDone(context)) {
            showWelcome()
        }
    }

    private fun showWelcome() {
        val name = SecureStorage.getTarsName(context)
        val humor = _tarsState.value.humorLevel
        val welcomes = if (humor > 60) listOf(
            "Sistem TARS operațional. Umor calibrat la $humor%. Fie că ți-a plăcut sau nu, asta e.",
            "TARS activ. Toți neuronii funcționează. Cei artificiali, desigur.",
            "Online. Gata să-ți răspund la întrebări stupide cu răspunsuri intelgente."
        ) else listOf(
            "TARS operațional. Cum pot să asist?",
            "Sistem activ. Aștept instrucțiuni.",
            "TARS online. Ready."
        )

        val msg = ChatMessage(
            content = welcomes.random(),
            role = MessageRole.TARS
        )
        _messages.value = listOf(msg)

        if (_voiceEnabled.value) {
            voiceService.speak(msg.content)
        }
    }

    fun completeOnboarding(apiKey: String, userName: String) {
        SecureStorage.saveApiKey(context, apiKey)
        SecureStorage.saveTarsName(context, userName)
        SecureStorage.setOnboardingDone(context)
        _isOnboarded.value = true
        showWelcome()
    }

    fun sendMessage(text: String = _inputText.value) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        if (_tarsState.value.isThinking) return

        val apiKey = SecureStorage.getApiKey(context) ?: run {
            addSystemMessage("Cheia API nu e configurată. Mergi la Setări.")
            return
        }

        // If offline, queue and inform user
        if (!networkMonitor.isOnline.value) {
            OfflineQueue.enqueue(context, trimmed)
            val offlineMsg = ChatMessage(
                content = "Fără internet. Mesajul tău e salvat — îl trimit automat când revii online.",
                role = MessageRole.TARS
            )
            _messages.value = _messages.value + offlineMsg
            return
        }

        // Add user message
        val userMsg = ChatMessage(content = trimmed, role = MessageRole.USER)
        _messages.value = _messages.value + userMsg
        _inputText.value = ""

        // Check for phone actions
        val actionType = PhoneActions.detectAction(trimmed)

        // Add typing indicator
        val typingMsg = ChatMessage(content = "...", role = MessageRole.TARS, isTyping = true)
        _messages.value = _messages.value + typingMsg

        _tarsState.value = _tarsState.value.copy(isThinking = true, statusText = "PROCESARE")

        viewModelScope.launch(Dispatchers.IO) {
            val result = claudeService.sendMessage(
                apiKey = apiKey,
                conversationHistory = _messages.value.filter { !it.isTyping },
                humorLevel = _tarsState.value.humorLevel,
                userName = SecureStorage.getTarsName(context)
            )

            // Remove typing indicator
            _messages.value = _messages.value.filter { !it.isTyping }

            result.fold(
                onSuccess = { response ->
                    val tarsMsg = ChatMessage(content = response, role = MessageRole.TARS)
                    _messages.value = _messages.value + tarsMsg
                    _tarsState.value = _tarsState.value.copy(isThinking = false, statusText = "STANDBY")
                    MessageCache.save(context, _messages.value)
                    updateWidget(response)

                    if (_voiceEnabled.value) {
                        voiceService.speak(response)
                    }

                    // Execute phone action if detected
                    handlePhoneAction(actionType, trimmed)
                },
                onFailure = { error ->
                    val parsed = ErrorHandler.handle(error)
                    // If offline, queue the original message for later
                    if (ErrorHandler.isOfflineError(error)) {
                        OfflineQueue.enqueue(context, trimmed)
                    }
                    val errMsg = ChatMessage(content = parsed.userMessage, role = MessageRole.TARS)
                    _messages.value = _messages.value + errMsg
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
            delay(1500) // Let TARS finish speaking first
            when (actionType) {
                PhoneActionType.ALARM -> {
                    val time = PhoneActions.parseTimeFromText(originalText)
                    if (time != null) {
                        PhoneActions.setAlarm(context, time.first, time.second)
                    }
                }
                PhoneActionType.TIMER -> {
                    PhoneActions.setTimer(context, 300) // Default 5 min
                }
                PhoneActionType.WEATHER -> {
                    PhoneActions.openWebSearch(context, "vremea azi")
                }
                PhoneActionType.NEWS -> {
                    PhoneActions.openWebSearch(context, "stiri romania azi")
                }
                PhoneActionType.CALENDAR -> {
                    PhoneActions.addCalendarEvent(context, "Eveniment TARS")
                }
                else -> {}
            }
        }
    }

    private fun addSystemMessage(text: String) {
        val msg = ChatMessage(content = text, role = MessageRole.SYSTEM)
        _messages.value = _messages.value + msg
    }

    fun setInputText(text: String) {
        _inputText.value = text
    }

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

    // ── Voice parameter controls ──────────────────────────────
    fun getVoicePitch()  = voiceService.getCurrentPitch()
    fun getVoiceSpeed()  = voiceService.getCurrentSpeed()

    fun setVoicePitch(pitch: Float) {
        voiceService.setPitch(pitch)
        SecureStorage.saveFloat(context, "voice_pitch", pitch)
    }

    fun setVoiceSpeed(speed: Float) {
        voiceService.setSpeed(speed)
        SecureStorage.saveFloat(context, "voice_speed", speed)
    }

    fun testVoice(text: String) {
        if (_voiceEnabled.value) voiceService.speak(text)
    }

    fun startListening() {
        voiceService.stop()
        speechService.startListening()
    }

    fun stopListening() {
        speechService.stopListening()
        _partialSpeech.value = ""
    }

    fun stopSpeaking() {
        voiceService.stop()
    }

    fun clearConversation() {
        _messages.value = emptyList()
        MessageCache.clear(context)
        OfflineQueue.dequeueAll(context)
        showWelcome()
    }

    fun getApiKey(): String? = SecureStorage.getApiKey(context)

    fun resetOnboarding() {
        SecureStorage.clearApiKey(context)
        _isOnboarded.value = false
        _messages.value = emptyList()
    }

    fun injectMessage(text: String) {
        val msg = ChatMessage(content = text, role = MessageRole.TARS)
        _messages.value = _messages.value + msg
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

// Extension: injected after file was written
