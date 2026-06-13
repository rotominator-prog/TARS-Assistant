@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.tars.assistant.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.tars.assistant.model.*
import com.tars.assistant.ui.theme.TarsColors
import com.tars.assistant.viewmodel.TarsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*




@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: TarsViewModel) {
    val messages by viewModel.messages.collectAsState()
    val tarsState by viewModel.tarsState.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val voiceEnabled by viewModel.voiceEnabled.collectAsState()
    val partialSpeech by viewModel.partialSpeech.collectAsState()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    var showMenu by remember { mutableStateOf(false) }
    var showVoiceSettings by remember { mutableStateOf(false) }

    // Auto-scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TarsColors.Background)
    ) {
        // Starfield background
        StarfieldBackground(Modifier.fillMaxSize())

        // Hex grid overlay
        HexGridBackground(Modifier.fillMaxSize(), alpha = 0.04f)

        // Scanlines overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val lineHeight = 4.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.05f),
                    topLeft = Offset(0f, y + lineHeight / 2),
                    size = androidx.compose.ui.geometry.Size(size.width, lineHeight / 2)
                )
                y += lineHeight
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // ── HEADER ──
            TarsHeader(
                tarsState = tarsState,
                isSpeaking = isSpeaking,
                isListening = isListening,
                onMenuClick = { showMenu = true }
            )

            // ── HUMOR SLIDER ──
            HumorBar(
                humorLevel = tarsState.humorLevel,
                onHumorChange = viewModel::setHumorLevel
            )

            // ── HONESTY SLIDER ──
            HonestyBar(
                honestyLevel = tarsState.honestyLevel,
                onHonestyChange = viewModel::setHonestyLevel
            )

            // ── SARCASM SLIDER ──
            SarcasmBar(
                sarcasmLevel = tarsState.sarcasmLevel,
                onSarcasmChange = viewModel::setSarcasmLevel
            )

            // ── MESSAGES ──
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
            @Suppress("OPT_IN_USAGE")
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
                    ) {
                        MessageBubble(message = message)
                    }
                }

                // Partial speech preview
                if (partialSpeech.isNotBlank()) {
                    item {
                        Text(
                            partialSpeech,
                            color = TarsColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            // ── QUICK ACTIONS ──
            QuickActionsBar(
                actions = DEFAULT_QUICK_ACTIONS,
                onAction = { prompt ->
                    viewModel.setInputText(prompt)
                    viewModel.sendMessage(prompt)
                }
            )

            // ── INPUT BAR ──
            InputBar(
                text = inputText,
                onTextChange = viewModel::setInputText,
                onSend = {
                    keyboardController?.hide()
                    viewModel.sendMessage()
                },
                onMicClick = {
                    if (isListening) viewModel.stopListening()
                    else viewModel.startListening()
                },
                isListening = isListening,
                isThinking = tarsState.isThinking,
                voiceEnabled = voiceEnabled
            )
        }

        // ── VOICE SETTINGS SHEET ──
        if (showVoiceSettings) {
            VoiceSettingsSheet(
                viewModel = viewModel,
                onDismiss = { showVoiceSettings = false }
            )
        }

        // ── DROPDOWN MENU ──
        if (showMenu) {
            TarsMenu(
                voiceEnabled = voiceEnabled,
                onToggleVoice = viewModel::toggleVoice,
                onClearChat = viewModel::clearConversation,
                onSettings = viewModel::resetOnboarding,
                onVoiceSettings = { showVoiceSettings = true },
                onDismiss = { showMenu = false }
            )
        }
    }
}

// ── HEADER ───────────────────────────────────────────────────
@Composable
fun TarsHeader(
    tarsState: TarsState,
    isSpeaking: Boolean,
    isListening: Boolean,
    onMenuClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "header")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "pulse"
    )

    val statusColor = when {
        tarsState.isThinking -> TarsColors.StatusWarn
        isListening -> TarsColors.StatusError
        isSpeaking -> TarsColors.AccentCyan
        else -> TarsColors.StatusGreen
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(TarsColors.SurfaceVariant.copy(0.8f), Color.Transparent)
                )
            )
            .border(BorderStroke(1.dp, TarsColors.Border), RoundedCornerShape(0.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Animated TARS Monolith
        TarsMonolith(
            modifier = Modifier.size(width = 26.dp, height = 44.dp),
            isThinking = tarsState.isThinking,
            isSpeaking = isSpeaking,
            isListening = isListening,
            humorLevel = tarsState.humorLevel
        )

        Column(modifier = Modifier.weight(1f)) {
        GlitchText(
            text = "T·A·R·S",
            active = tarsState.isThinking
        )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tarsState.statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
                Text("//", color = TarsColors.TextSecondary, fontSize = 9.sp)
                Text(
                    "UMOR: ${tarsState.humorLevel}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = TarsColors.TextSecondary
                )
            }
        }

        // Live waveform when speaking or listening
        if (isSpeaking || isListening) {
            LiveWaveform(
                modifier = Modifier.size(width = 60.dp, height = 24.dp),
                isActive = true,
                color = if (isListening) TarsColors.StatusError else TarsColors.AccentCyan
            )
        }

        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(statusColor.copy(alpha = pulse), shape = RoundedCornerShape(50))
                .drawBehind {
                    drawCircle(statusColor.copy(alpha = pulse * 0.3f), radius = size.minDimension)
                }
        )

        IconButton(onClick = onMenuClick) {
            Icon(
                Icons.Default.Menu,
                contentDescription = "Meniu",
                tint = TarsColors.TextSecondary
            )
        }
    }
}

// ── HUMOR BAR ────────────────────────────────────────────────
@Composable
fun HumorBar(humorLevel: Int, onHumorChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TarsColors.Surface)
            .border(BorderStroke(Dp.Hairline, TarsColors.Border))
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("UMOR", style = MaterialTheme.typography.labelSmall, color = TarsColors.TextSecondary)
        Slider(
            value = humorLevel.toFloat(),
            onValueChange = { onHumorChange(it.toInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = TarsColors.AccentCyan,
                activeTrackColor = TarsColors.AccentCyanDim,
                inactiveTrackColor = TarsColors.Border
            )
        )
        Text(
            "$humorLevel%",
            style = MaterialTheme.typography.labelSmall,
            color = TarsColors.AccentCyan,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End
        )
    }
}

// ── HONESTY BAR ──────────────────────────────────────────────
@Composable
fun HonestyBar(honestyLevel: Int, onHonestyChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TarsColors.Surface)
            .border(BorderStroke(Dp.Hairline, TarsColors.Border))
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("SINCER", style = MaterialTheme.typography.labelSmall, color = TarsColors.TextSecondary)
        Slider(
            value = honestyLevel.toFloat(),
            onValueChange = { onHonestyChange(it.toInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = TarsColors.AccentCyan,
                activeTrackColor = TarsColors.AccentCyanDim,
                inactiveTrackColor = TarsColors.Border
            )
        )
        Text(
            "$honestyLevel%",
            style = MaterialTheme.typography.labelSmall,
            color = TarsColors.AccentCyan,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End
        )
    }
}

// ── SARCASM BAR ──────────────────────────────────────────────
@Composable
fun SarcasmBar(sarcasmLevel: Int, onSarcasmChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TarsColors.Surface)
            .border(BorderStroke(Dp.Hairline, TarsColors.Border))
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("SARCASM", style = MaterialTheme.typography.labelSmall, color = TarsColors.TextSecondary)
        Slider(
            value = sarcasmLevel.toFloat(),
            onValueChange = { onSarcasmChange(it.toInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = TarsColors.AccentCyan,
                activeTrackColor = TarsColors.AccentCyanDim,
                inactiveTrackColor = TarsColors.Border
            )
        )
        Text(
            "$sarcasmLevel%",
            style = MaterialTheme.typography.labelSmall,
            color = TarsColors.AccentCyan,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End
        )
    }
}

// ── MESSAGE BUBBLE ───────────────────────────────────────────
@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    val isSystem = message.role == MessageRole.SYSTEM

    if (isSystem) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                message.content,
                style = MaterialTheme.typography.labelSmall,
                color = TarsColors.StatusWarn,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(TarsColors.Surface, RoundedCornerShape(4.dp))
                    .border(1.dp, TarsColors.StatusWarn.copy(0.3f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        return
    }

    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault())
        .format(Date(message.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Text(
            if (isUser) "TU // $timeStr" else "TARS // $timeStr",
            style = MaterialTheme.typography.labelSmall,
            color = if (isUser) TarsColors.TextSecondary else TarsColors.AccentCyanDim,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        if (message.isTyping) {
            // Typing indicator
            Box(
                modifier = Modifier
                    .background(TarsColors.TarsBubble, RoundedCornerShape(2.dp, 12.dp, 12.dp, 2.dp))
                    .border(1.dp, TarsColors.Border, RoundedCornerShape(2.dp, 12.dp, 12.dp, 2.dp))
                    .padding(12.dp, 14.dp)
            ) {
                TypingIndicator()
            }
        } else {
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .background(
                        if (isUser) TarsColors.UserBubble else TarsColors.TarsBubble,
                        if (isUser) RoundedCornerShape(12.dp, 2.dp, 2.dp, 12.dp)
                        else RoundedCornerShape(2.dp, 12.dp, 12.dp, 2.dp)
                    )
                    .border(
                        1.dp,
                        if (isUser) TarsColors.AccentCyan.copy(0.2f) else TarsColors.Border,
                        if (isUser) RoundedCornerShape(12.dp, 2.dp, 2.dp, 12.dp)
                        else RoundedCornerShape(2.dp, 12.dp, 12.dp, 2.dp)
                    )
                    .then(
                        if (!isUser) Modifier.drawBehind {
                            drawRect(
                                TarsColors.AccentCyan,
                                topLeft = Offset(0f, 0f),
                                size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)
                            )
                        } else Modifier
                    )
                    .padding(start = if (!isUser) 14.dp else 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
            ) {
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TarsColors.TextPrimary,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

// ── TYPING INDICATOR ─────────────────────────────────────────
@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { i ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(600, delayMillis = i * 150),
                    RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(TarsColors.AccentCyan.copy(alpha), RoundedCornerShape(50))
            )
        }
    }
}

// ── WAVEFORM ─────────────────────────────────────────────────
@Composable
fun WaveformIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            val height by infiniteTransition.animateFloat(
                initialValue = 4f, targetValue = 18f,
                animationSpec = infiniteRepeatable(
                    tween(300 + i * 80, easing = EaseInOutSine),
                    RepeatMode.Reverse
                ),
                label = "wave$i"
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(height.dp)
                    .background(TarsColors.AccentCyan.copy(0.7f), RoundedCornerShape(1.dp))
            )
        }
    }
}

// ── QUICK ACTIONS ────────────────────────────────────────────
@Composable
fun QuickActionsBar(actions: List<QuickAction>, onAction: (String) -> Unit) {
            @Suppress("OPT_IN_USAGE")
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(TarsColors.Surface)
            .border(BorderStroke(Dp.Hairline, TarsColors.Border))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(actions) { action ->
            OutlinedButton(
                onClick = { onAction(action.prompt) },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TarsColors.TextSecondary
                ),
                border = BorderStroke(1.dp, TarsColors.Border),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    "${action.icon} ${action.label}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// ── INPUT BAR ────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    isListening: Boolean,
    isThinking: Boolean,
    voiceEnabled: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic")
    val micPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "micPulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TarsColors.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Text input
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
            @Suppress("OPT_IN_USAGE")
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TarsColors.TextPrimary),
            cursorBrush = SolidColor(TarsColors.AccentCyan),
            modifier = Modifier
                .weight(1f)
                .background(TarsColors.Background, RoundedCornerShape(4.dp))
                .border(1.dp, TarsColors.Border, RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(
                        "Scrie sau vorbește cu TARS...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TarsColors.TextSecondary
                    )
                }
                inner()
            },
            maxLines = 4
        )

        // Mic button
        if (voiceEnabled) {
            IconButton(
                onClick = onMicClick,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (isListening) TarsColors.StatusError.copy(micPulse * 0.3f)
                        else TarsColors.SurfaceVariant,
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        1.dp,
                        if (isListening) TarsColors.StatusError else TarsColors.Border,
                        RoundedCornerShape(4.dp)
                    )
            ) {
                Icon(
                    if (isListening) Icons.Default.Close else Icons.Default.Call,
                    contentDescription = "Microfon",
                    tint = if (isListening) TarsColors.StatusError else TarsColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Send button
        IconButton(
            onClick = onSend,
            enabled = text.isNotBlank() && !isThinking,
            modifier = Modifier
                .size(44.dp)
                .background(
                    TarsColors.SurfaceVariant,
                    RoundedCornerShape(4.dp)
                )
                .border(1.dp, TarsColors.AccentCyanDim, RoundedCornerShape(4.dp))
        ) {
            Icon(
                Icons.Default.Send,
                contentDescription = "Trimite",
                tint = if (text.isNotBlank() && !isThinking) TarsColors.AccentCyan else TarsColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── MENU ─────────────────────────────────────────────────────
@Composable
fun TarsMenu(
    voiceEnabled: Boolean,
    onToggleVoice: () -> Unit,
    onClearChat: () -> Unit,
    onSettings: () -> Unit,
    onVoiceSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .padding(top = 60.dp, end = 16.dp)
                .width(220.dp)
                .background(TarsColors.Surface, RoundedCornerShape(4.dp))
                .border(1.dp, TarsColors.Border, RoundedCornerShape(4.dp))
                .clickable(enabled = false) {}
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "CONFIGURARE TARS",
                style = MaterialTheme.typography.labelSmall,
                color = TarsColors.AccentCyan,
                modifier = Modifier.padding(8.dp)
            )
            Divider(color = TarsColors.Border)

            TarsMenuItem(
                icon = if (voiceEnabled) Icons.Default.Add else Icons.Default.Close,
                label = if (voiceEnabled) "Voce: ACTIVĂ" else "Voce: INACTIVĂ",
                onClick = { onToggleVoice(); onDismiss() }
            )
            TarsMenuItem(
                icon = Icons.Default.Delete,
                label = "Șterge conversația",
                onClick = { onClearChat(); onDismiss() }
            )
            TarsMenuItem(
                icon = Icons.Default.Settings,
                label = "Setări voce",
                onClick = { onVoiceSettings(); onDismiss() }
            )
            TarsMenuItem(
                icon = Icons.Default.Lock,
                label = "Schimbă cheia API",
                onClick = { onSettings(); onDismiss() }
            )
        }
    }
}

@Composable
fun TarsMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TarsColors.TextSecondary, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TarsColors.TextPrimary)
    }
}
