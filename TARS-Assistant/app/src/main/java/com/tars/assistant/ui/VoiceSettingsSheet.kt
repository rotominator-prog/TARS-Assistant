package com.tars.assistant.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tars.assistant.model.VoiceGender
import com.tars.assistant.model.VoicePreset
import com.tars.assistant.ui.theme.TarsColors
import com.tars.assistant.viewmodel.TarsViewModel

@Composable
fun VoiceSettingsSheet(
    viewModel: TarsViewModel,
    onDismiss: () -> Unit
) {
    val voiceEnabled by viewModel.voiceEnabled.collectAsState()
    // FIX: citim din StateFlow reactiv → sliderele se mișcă în timp real
    val pitch by viewModel.voicePitch.collectAsState()
    val speed by viewModel.voiceSpeed.collectAsState()
    val gender by viewModel.voiceGender.collectAsState()
    val preset by viewModel.voicePreset.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TarsColors.Background.copy(0.85f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TarsColors.Surface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .border(
                    BorderStroke(1.dp, TarsColors.Border),
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
                .clickable(enabled = false) {}
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(3.dp)
                    .background(TarsColors.Border, RoundedCornerShape(2.dp))
                    .align(Alignment.CenterHorizontally)
            )

            Text(
                "CONFIGURARE VOCE TARS",
                style = MaterialTheme.typography.labelSmall,
                color = TarsColors.AccentCyan,
                letterSpacing = 2.sp
            )

            Divider(color = TarsColors.Border)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("RĂSPUNS VOCAL", style = MaterialTheme.typography.bodyMedium,
                        color = TarsColors.TextPrimary)
                    Text("TARS vorbește cu tine",
                        style = MaterialTheme.typography.labelSmall,
                        color = TarsColors.TextSecondary)
                }
                Switch(
                    checked = voiceEnabled,
                    onCheckedChange = { viewModel.toggleVoice() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TarsColors.AccentCyan,
                        checkedTrackColor = TarsColors.AccentCyanDim.copy(0.5f),
                        uncheckedThumbColor = TarsColors.TextSecondary,
                        uncheckedTrackColor = TarsColors.Border
                    )
                )
            }

            AnimatedVisibility(visible = voiceEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    // Voice preset selector
                    Text("PRESET VOCE", style = MaterialTheme.typography.labelSmall,
                        color = TarsColors.TextSecondary, letterSpacing = 1.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GenderChip("TARS", preset == VoicePreset.TARS) {
                            viewModel.setVoicePreset(VoicePreset.TARS)
                        }
                        GenderChip("JARVIS", preset == VoicePreset.JARVIS) {
                            viewModel.setVoicePreset(VoicePreset.JARVIS)
                        }
                        GenderChip("CUSTOM", preset == VoicePreset.CUSTOM) {
                            viewModel.setVoicePreset(VoicePreset.CUSTOM)
                        }
                    }
                    Text(
                        when (preset) {
                            VoicePreset.TARS -> "Grav, robotic, deliberat. Română."
                            VoicePreset.JARVIS -> "Britanic, masculin, elegant și fluid. Engleză (en-GB)."
                            VoicePreset.CUSTOM -> "Reglaje manuale din opțiunile de mai jos."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TarsColors.TextSecondary.copy(0.6f), fontSize = 9.sp
                    )

                    // Gender selector
                    Text("GEN VOCE", style = MaterialTheme.typography.labelSmall,
                        color = TarsColors.TextSecondary, letterSpacing = 1.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GenderChip("MASCULIN", gender == VoiceGender.MALE) {
                            viewModel.setVoiceGender(VoiceGender.MALE)
                        }
                        GenderChip("FEMININ", gender == VoiceGender.FEMALE) {
                            viewModel.setVoiceGender(VoiceGender.FEMALE)
                        }
                        GenderChip("AUTO", gender == VoiceGender.AUTO) {
                            viewModel.setVoiceGender(VoiceGender.AUTO)
                        }
                    }

                    // Pitch slider (reactiv)
                    VoiceSlider(
                        label = "TON (PITCH)",
                        description = "Mai jos = mai robotic / Mai sus = mai natural",
                        value = pitch,
                        range = 0.5f..1.5f,
                        displayValue = { "%.2f".format(it) },
                        onValueChange = { viewModel.setVoicePitch(it) }
                    )

                    // Speed slider (reactiv)
                    VoiceSlider(
                        label = "VITEZĂ",
                        description = "Mai lent = mai deliberat (recomandat: 0.85)",
                        value = speed,
                        range = 0.5f..1.5f,
                        displayValue = { "%.2f".format(it) },
                        onValueChange = { viewModel.setVoiceSpeed(it) }
                    )

                    OutlinedButton(
                        onClick = {
                            val phrase = if (preset == VoicePreset.JARVIS)
                                "Good evening. All systems are online and functioning within normal parameters."
                            else
                                "Vocea mea e calibrată. Umorul meu, de asemenea. " +
                                "Onestitate 90 la sută: asta suna mai bine decât mă așteptam."
                            viewModel.testVoice(phrase)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, TarsColors.AccentCyanDim),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TarsColors.AccentCyan
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("TESTEAZĂ VOCEA", style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.sp)
                    }

                    TarsInfoBox(
                        title = "INFO // CALITATE VOCALĂ",
                        content = "TARS folosește motorul Text-to-Speech instalat pe telefonul tău. " +
                            "Pentru voce masculină și calitate bună în română, instalează Google " +
                            "Text-to-Speech din Play Store și descarcă pachetul vocal românesc. " +
                            "Dacă nu există voce masculină românească, TARS o folosește pe cea engleză masculină."
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GenderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) TarsColors.AccentCyanDim.copy(0.25f) else TarsColors.Surface,
                RoundedCornerShape(4.dp)
            )
            .border(
                1.dp,
                if (selected) TarsColors.AccentCyan else TarsColors.Border,
                RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) TarsColors.AccentCyan else TarsColors.TextSecondary,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun VoiceSlider(
    label: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    displayValue: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = TarsColors.TextSecondary, letterSpacing = 1.sp)
            Text(displayValue(value), style = MaterialTheme.typography.labelSmall,
                color = TarsColors.AccentCyan)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = TarsColors.AccentCyan,
                activeTrackColor = TarsColors.AccentCyanDim,
                inactiveTrackColor = TarsColors.Border
            )
        )
        Text(description, style = MaterialTheme.typography.labelSmall,
            color = TarsColors.TextSecondary.copy(0.6f), fontSize = 9.sp)
    }
}
