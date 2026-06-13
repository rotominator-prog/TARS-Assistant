package com.tars.assistant.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tars.assistant.model.AiProvider
import com.tars.assistant.ui.theme.TarsColors

/**
 * Onboarding. Providerul implicit e Gemini (gratuit, calitate bună pe română).
 * Utilizatorul poate adăuga ceilalți provideri (Groq, Cerebras etc.) ulterior
 * din Setări, pentru lanțul de fallback.
 */
@Composable
fun OnboardingScreen(onComplete: (provider: AiProvider, apiKey: String, name: String) -> Unit) {
    val defaultProvider = AiProvider.GEMINI

    var step by remember { mutableStateOf(0) }
    var apiKey by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(modifier = Modifier.fillMaxSize().background(TarsColors.Background)) {
        StarfieldBackground(Modifier.fillMaxSize())
        HexGridBackground(Modifier.fillMaxSize(), alpha = 0.05f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .drawBehind {
                        drawCircle(
                            color = TarsColors.AccentCyan.copy(alpha = glowAlpha * 0.3f),
                            radius = size.minDimension / 2 + 20.dp.toPx()
                        )
                    }
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(80.dp)
                        .border(
                            2.dp,
                            Brush.verticalGradient(
                                listOf(TarsColors.AccentCyan, TarsColors.AccentCyanDim)
                            ),
                            RoundedCornerShape(4.dp)
                        )
                ) {
                    repeat(3) { i ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(4.dp)
                                .align(Alignment.Center)
                                .offset(y = ((i - 1) * 14).dp)
                                .background(
                                    TarsColors.AccentCyan.copy(alpha = glowAlpha * 0.7f),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "T · A · R · S",
                style = MaterialTheme.typography.displayLarge,
                color = TarsColors.AccentCyan,
                letterSpacing = 8.sp
            )
            Text(
                "TACTICAL ASSISTANCE & RESPONSE SYSTEM",
                style = MaterialTheme.typography.labelSmall,
                color = TarsColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                "v1.0.0 // SAMSUNG S24 ULTRA",
                style = MaterialTheme.typography.labelSmall,
                color = TarsColors.TextSecondary.copy(0.5f),
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(Modifier.height(40.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) { i ->
                    Box(
                        modifier = Modifier
                            .size(width = if (i == step) 24.dp else 8.dp, height = 4.dp)
                            .background(
                                if (i <= step) TarsColors.AccentCyan else TarsColors.Border,
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            AnimatedContent(targetState = step, label = "step") { currentStep ->
                when (currentStep) {
                    0 -> Step0ApiKey(
                        apiKey = apiKey,
                        onApiKeyChange = { apiKey = it; error = "" },
                        showKey = showKey,
                        onToggleShow = { showKey = !showKey },
                        error = error,
                        onNext = {
                            if (apiKey.trim().length < 15) {
                                error = "Cheia API pare invalidă. Verifică și încearcă din nou."
                            } else {
                                step = 1
                            }
                        }
                    )
                    1 -> Step1UserName(
                        userName = userName,
                        onUserNameChange = { userName = it; error = "" },
                        onComplete = {
                            onComplete(
                                defaultProvider,
                                apiKey.trim(),
                                userName.trim().ifBlank { "Utilizator" }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Step0ApiKey(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    showKey: Boolean,
    onToggleShow: () -> Unit,
    error: String,
    onNext: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TarsInfoBox(
            title = "PASUL 1 // CHEIE API GRATUITĂ",
            content = "TARS are nevoie de o cheie API pentru a gândi. " +
                "Folosim Google Gemini — gratuit, fără card.\n\n" +
                "1. Deschide aistudio.google.com/apikey\n" +
                "2. Loghează-te cu un cont Google\n" +
                "3. Apasă \"Create API key\"\n" +
                "4. Copiază cheia (începe cu AIza...) și lipește-o mai jos\n\n" +
                "Cheia rămâne stocată CRIPTAT doar pe telefonul tău. " +
                "Poți adăuga mai târziu Groq și Cerebras în Setări, ca rezerve."
        )

        Spacer(Modifier.height(20.dp))

        TarsTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = "AIza...",
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingContent = {
                TextButton(onClick = onToggleShow) {
                    Text(
                        if (showKey) "ASCUNDE" else "ARATĂ",
                        color = TarsColors.AccentCyanDim,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        )

        if (error.isNotBlank()) {
            Text(
                error,
                color = TarsColors.StatusError,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        TarsButton("CONTINUĂ →", onClick = onNext, enabled = apiKey.isNotBlank())
    }
}

@Composable
private fun Step1UserName(
    userName: String,
    onUserNameChange: (String) -> Unit,
    onComplete: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TarsInfoBox(
            title = "PASUL 2 // IDENTIFICARE",
            content = "Cum să te adreseze TARS?\n\n" +
                "Poți folosi numele tău sau orice prefix. " +
                "TARS va personaliza răspunsurile în funcție de asta."
        )

        Spacer(Modifier.height(20.dp))

        TarsTextField(
            value = userName,
            onValueChange = onUserNameChange,
            label = "Numele tău (opțional)"
        )

        Spacer(Modifier.height(20.dp))

        TarsButton("INIȚIALIZARE TARS ▶", onClick = onComplete)

        Spacer(Modifier.height(12.dp))
        Text(
            "Poți modifica setările oricând din meniu.",
            color = TarsColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}

// ── Reusable Components ───────────────────────────────────────

@Composable
fun TarsInfoBox(title: String, content: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TarsColors.Surface, RoundedCornerShape(4.dp))
            .border(1.dp, TarsColors.Border, RoundedCornerShape(4.dp))
            .padding(16.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = TarsColors.AccentCyan,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Divider(color = TarsColors.Border, thickness = 1.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            content,
            style = MaterialTheme.typography.bodyMedium,
            color = TarsColors.TextPrimary,
            lineHeight = 20.sp
        )
    }
}

@Composable
fun TarsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingContent: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(label, color = TarsColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        },
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        trailingIcon = trailingContent,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TarsColors.AccentCyan,
            unfocusedBorderColor = TarsColors.Border,
            focusedTextColor = TarsColors.TextBright,
            unfocusedTextColor = TarsColors.TextPrimary,
            cursorColor = TarsColors.AccentCyan,
            focusedContainerColor = TarsColors.Surface,
            unfocusedContainerColor = TarsColors.Surface
        ),
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(4.dp)
    )
}

@Composable
fun TarsButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = TarsColors.SurfaceVariant,
            contentColor = TarsColors.AccentCyan,
            disabledContainerColor = TarsColors.Border,
            disabledContentColor = TarsColors.TextSecondary
        ),
        border = BorderStroke(1.dp, if (enabled) TarsColors.AccentCyanDim else TarsColors.Border),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, letterSpacing = 2.sp)
    }
}
