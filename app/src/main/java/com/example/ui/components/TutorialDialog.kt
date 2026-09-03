package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AncActiveGreen
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.MintSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

data class TutorialStep(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconTint: Color,
    val description: String,
    val highlights: List<String>,
    val tip: String
)

private val tutorialSteps = listOf(
    TutorialStep(
        title = "Acoustic Superposition & ANC",
        subtitle = "STEP 1 OF 5: THE PHYSICS OF CANCELLATION",
        icon = Icons.Default.GraphicEq,
        iconTint = CyanPrimary,
        description = "Active Noise Cancellation (ANC) generates an inverted acoustic wave (180° out of phase). When the anti-noise wave meets incoming environmental noise in your ear canal, the two waveforms cancel destructively.",
        highlights = listOf(
            "Microphone captures reference ambient noise",
            "Filtered-X LMS adaptive algorithm computes exact anti-phase",
            "Destructive interference cancels audible acoustic pressure"
        ),
        tip = "Optimal cancellation occurs for periodic low-frequency noises (80 Hz - 1.5 kHz) like engines, trains, and HVAC fans."
    ),
    TutorialStep(
        title = "Headphone & Mic Setup",
        subtitle = "STEP 2 OF 5: PREVENTING FEEDBACK LOOPS",
        icon = Icons.Default.Headphones,
        iconTint = MintSecondary,
        description = "You MUST use wired headphones/earbuds or low-latency headsets. If you play anti-noise through the phone's loudspeaker, the microphone captures it again, creating a high-pitch acoustic screech (feedback loop).",
        highlights = listOf(
            "Always plug in wired or USB-C earphones before activating ANC",
            "Ensure the reference microphone is pointed outward toward ambient noise",
            "Never place the phone microphone against the headphone speaker"
        ),
        tip = "Check the 'Devices' tab to verify that your headset is recognized with low-latency capabilities."
    ),
    TutorialStep(
        title = "Acoustic Delay Calibration",
        subtitle = "STEP 3 OF 5: MILLISECOND SYNCHRONIZATION",
        icon = Icons.Default.Speed,
        iconTint = CyanPrimary,
        description = "Sound travels at ~343 m/s (~1 ms per foot). If the anti-noise arrives too late, it will amplify the sound instead of canceling it. Precise calibration aligns the phase perfectly.",
        highlights = listOf(
            "Use the 'Calibrate' tab to run an impulse chirp test",
            "The system measures hardware round-trip and secondary path delay",
            "Recommended delay is automatically programmed into the DSP engine"
        ),
        tip = "Run calibration whenever you switch headphones or adjust earbud seating."
    ),
    TutorialStep(
        title = "Native Integrated Equalizer",
        subtitle = "STEP 4 OF 5: 5-BAND FREQUENCY SHAPING",
        icon = Icons.Default.Tune,
        iconTint = MintSecondary,
        description = "Fine-tune your acoustic listening profile with the high-performance 5-Band Biquad Equalizer directly embedded in the native C++ audio thread.",
        highlights = listOf(
            "Sub-Bass (60Hz) & Bass (250Hz): Notch out engine drone & HVAC hum",
            "Mid (1kHz) & High-Mid (4kHz): Boost voice clarity in Transparency/Monitor mode",
            "Treble (12kHz): Soften high-pitch electronic hiss & mechanical friction"
        ),
        tip = "Use the built-in quick presets like 'Rumble Notch' or 'Vocal Clarity' for instant tuning."
    ),
    TutorialStep(
        title = "Safety & Emergency Guardrails",
        subtitle = "STEP 5 OF 5: PROTECTING YOUR EARS",
        icon = Icons.Default.Security,
        iconTint = AncActiveGreen,
        description = "Your hearing safety is paramount. The DSP engine includes multiple hardware-level and software protections to prevent dangerous volume spikes.",
        highlights = listOf(
            "Hard Safety Limiter clamps any output spike to -0.98 FS",
            "Adaptive Filter Divergence guard detects feedback and auto-zeros weights",
            "Prominent Emergency Kill Switch instantly shuts down audio streams"
        ),
        tip = "Start at low ANC Strength (0.5x) and gradually increase to find your sweet spot."
    )
)

@Composable
fun TutorialDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStepIndex by remember { mutableIntStateOf(0) }
    val step = tutorialSteps[currentStepIndex]
    val isLastStep = currentStepIndex == tutorialSteps.size - 1

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 24.dp)
                .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = DarkSurface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF131D33),
                                DarkBackground
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                // Top Header Row with Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(step.iconTint.copy(alpha = 0.15f), CircleShape)
                                .border(1.dp, step.iconTint.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = step.icon,
                                contentDescription = null,
                                tint = step.iconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "ANC TUTORIAL & GUIDE",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = step.subtitle,
                                color = CyanPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("tutorial_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Tutorial",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Animated step content
                AnimatedContent(
                    targetState = step,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "tutorial_step_transition"
                ) { targetStep ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = targetStep.title,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = targetStep.description,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Highlights box
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .border(1.dp, DarkSurfaceBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            targetStep.highlights.forEach { item ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .size(6.dp)
                                            .background(targetStep.iconTint, CircleShape)
                                    )
                                    Text(
                                        text = item,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Pro Tip Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MintSecondary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                .border(1.dp, MintSecondary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "TIP:",
                                    color = MintSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = targetStep.tip,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Step indicator dots
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tutorialSteps.forEachIndexed { idx, _ ->
                        val isCurrent = idx == currentStepIndex
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (isCurrent) 18.dp else 6.dp, 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (isCurrent) CyanPrimary else DarkSurfaceBorder
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom Nav Buttons: Previous / Next / Got It
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStepIndex > 0) {
                        OutlinedButton(
                            onClick = { currentStepIndex-- },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("tutorial_prev_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkSurfaceBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Previous",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Previous", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Button(
                        onClick = {
                            if (isLastStep) {
                                onDismiss()
                            } else {
                                currentStepIndex++
                            }
                        },
                        modifier = Modifier
                            .weight(if (currentStepIndex > 0) 1.5f else 1f)
                            .height(44.dp)
                            .testTag("tutorial_next_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLastStep) AncActiveGreen else CyanPrimary
                        )
                    ) {
                        Text(
                            text = if (isLastStep) "GOT IT, LET'S GO!" else "Next Step",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isLastStep) Icons.Default.Check else Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
