package com.tars.assistant.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tars.assistant.ui.theme.TarsColors
import kotlinx.coroutines.delay

@Composable
fun TarsMonolith(
    modifier: Modifier = Modifier,
    isThinking: Boolean = false,
    isSpeaking: Boolean = false,
    isListening: Boolean = false,
    humorLevel: Int = 75
) {
    val infiniteTransition = rememberInfiniteTransition(label = "monolith")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (isThinking) 400 else 2000, easing = EaseInOutSine),
            RepeatMode.Reverse
        ), label = "glow"
    )
    val accentColor = when {
        isListening -> TarsColors.StatusError
        isSpeaking  -> TarsColors.AccentCyan
        isThinking  -> TarsColors.StatusWarn
        else        -> TarsColors.AccentCyan
    }
    Box(
        modifier = modifier
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(36.dp)
                .background(accentColor.copy(alpha = glowPulse * 0.2f))
        )
    }
}

@Composable
fun StarfieldBackground(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(TarsColors.Background))
}

@Composable
fun HexGridBackground(modifier: Modifier = Modifier, alpha: Float = 0.06f) {
    Box(modifier = modifier)
}

@Composable
fun BootSequence(onComplete: () -> Unit) {
    val bootLines = listOf(
        "TARS SYSTEM v1.0.0",
        "Initializare nucleu AI..........  [OK]",
        "Calibrare umor: 75%..............  [OK]",
        "Conectare Claude API.............  [OK]",
        "Module vocale....................  [OK]",
        "TARS OPERATIONAL."
    )
    var displayedLines by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(Unit) {
        for (line in bootLines) {
            delay(200)
            displayedLines = displayedLines + line
        }
        delay(800)
        onComplete()
    }
    Box(
        modifier = Modifier.fillMaxSize().background(TarsColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = Modifier.padding(32.dp)) {
            displayedLines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (line.contains("OK") || line.contains("OPERATIONAL"))
                        TarsColors.StatusGreen else TarsColors.TextSecondary
                )
            }
        }
    }
}

@Composable
fun GlitchText(text: String, modifier: Modifier = Modifier, active: Boolean = false) {
    Text(
        text = text,
        color = TarsColors.AccentCyan,
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier
    )
}

@Composable
fun LiveWaveform(
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    color: Color = TarsColors.AccentCyan,
    barCount: Int = 5
) {
    if (!isActive) return
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(barCount) { i ->
            val h by infiniteTransition.animateFloat(
                initialValue = 4f, targetValue = 20f,
                animationSpec = infiniteRepeatable(tween(300 + i * 80, easing = EaseInOutSine), RepeatMode.Reverse),
                label = "w$i"
            )
            Box(modifier = Modifier.width(2.dp).height(h.dp).background(color))
        }
    }
}
