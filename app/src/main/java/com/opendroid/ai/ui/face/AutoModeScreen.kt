// Full-screen, voice-only mode: the robot face IS the interface.
//
// Nothing here talks to the agent or to the recognizer — every input is a
// callback and every output is a parameter. AutoModeHost owns that wiring, so
// this file stays previewable and its status text stays unit-testable.

package com.opendroid.ai.ui.face

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.ui.theme.LocalOpenDroidColors

/**
 * One short line telling the user what is happening. In Auto mode this is the
 * only text on screen, so it has to carry the whole status by itself.
 *
 * [isListening] is the microphone's own state, which leads the agent's: the mic
 * is already open while the agent is still Idle.
 */
fun autoModeStatusLabel(state: AgentState, isListening: Boolean): String = when {
    isListening -> "Listening…"
    state is AgentState.Thinking -> "Thinking…"
    state is AgentState.PlanProposed -> "Waiting for your approval"
    state is AgentState.ExecutingPlan -> state.currentStepDesc.ifBlank { "Working on it…" }
    state is AgentState.Speaking -> "Speaking…"
    state is AgentState.Error -> "Something went wrong"
    state is AgentState.Listening -> "Listening…"
    else -> "Tap to speak"
}

@Composable
fun AutoModeScreen(
    state: AgentState,
    isListening: Boolean,
    transcript: String,
    errorMessage: String?,
    onToggleListening: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    amplitude: Float = 0f,
    onApprovePlan: (() -> Unit)? = null,
    onRejectPlan: (() -> Unit)? = null,
) {
    val colors = LocalOpenDroidColors.current
    val awaitingApproval = state is AgentState.PlanProposed

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Leave hands-free mode",
                tint = colors.textSecondary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // The face takes half the height and the full width it is given; the
            // component itself scales, so nothing here is a magic dp.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                RobotFace(
                    state = state,
                    amplitude = amplitude,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(maxWidth)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = autoModeStatusLabel(state, isListening),
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            // Live transcript. Reserved height so the face does not jump every time
            // a partial result arrives or disappears.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(top = 12.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                val subtitle = errorMessage ?: transcript
                val subtitleAlpha by animateFloatAsState(
                    targetValue = if (subtitle.isBlank()) 0f else 1f,
                    animationSpec = tween(180),
                    label = "subtitleAlpha"
                )
                Text(
                    text = subtitle,
                    color = if (errorMessage != null) colors.accentRed else colors.textSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(subtitleAlpha)
                )
            }
        }

        // A proposed plan is the one moment Auto mode cannot stay voice-only: the
        // user has to say yes or no to something with real consequences, so the
        // choice is shown as two explicit buttons rather than inferred from speech.
        if (awaitingApproval && onApprovePlan != null && onRejectPlan != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onRejectPlan,
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, colors.borderColor),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Text("Reject", color = colors.textSecondary)
                }
                Button(
                    onClick = onApprovePlan,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreenButton),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Text("Approve", color = colors.background, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            MicButton(
                isListening = isListening,
                amplitude = amplitude,
                onClick = onToggleListening,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            )
        }
    }
}

/**
 * The only control in Auto mode. It grows with the voice amplitude so the user
 * can see the microphone is actually hearing them without reading any text.
 */
@Composable
private fun MicButton(
    isListening: Boolean,
    amplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalOpenDroidColors.current
    val scale by animateFloatAsState(
        targetValue = if (isListening) 1f + amplitude * 0.18f else 1f,
        animationSpec = tween(90),
        label = "micScale"
    )
    val tint = if (isListening) colors.accentCyan else colors.accentNeonGreen

    Box(
        modifier = modifier
            .size(76.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(colors.cardBackground)
            .border(1.dp, tint, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (isListening) "Stop listening" else "Start listening",
            tint = tint,
            modifier = Modifier.size(30.dp)
        )
    }
}
