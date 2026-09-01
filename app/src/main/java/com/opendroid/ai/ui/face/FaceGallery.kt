// Every expression, in every style, on one screen.
//
// Expressions are hard to review in situ: most last a second or two, and the
// phase 4 vocabulary has no trigger yet at all. Long-pressing the face in
// hands-free mode opens this, so the drawing can be judged as a set — which is
// the only way to tell whether two expressions read as the same face.
//
// It doubles as the style picker: both styles draw the same expressions, so
// seeing them side by side is the honest way to choose between them.

package com.opendroid.ai.ui.face

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import com.opendroid.ai.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.ui.theme.LocalOpenDroidColors

@Composable
fun FaceGallery(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalOpenDroidColors.current
    val styleStore = rememberFaceStyleStore()
    val activeStyle by styleStore.style.collectAsState()
    // The tab starts on whatever the user is actually using, so the gallery opens
    // showing the face they just long-pressed.
    var shownStyle by remember { mutableStateOf(activeStyle) }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Same pure black as hands-free, so switching between them is not a
            // change of surface.
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = shownStyle.galleryTitle,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.gallery_close),
                        tint = colors.textSecondary,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FaceStyle.entries.forEach { style ->
                    StyleTab(
                        style = style,
                        selected = style == shownStyle,
                        inUse = style == activeStyle,
                        onClick = { shownStyle = style },
                        onUse = { styleStore.select(style) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(FaceExpression.entries) { expression ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RobotFace(
                            // The gallery drives the drawing directly; the agent
                            // state it is handed is never read.
                            state = AgentState.Idle,
                            expressionOverride = expression,
                            styleOverride = shownStyle,
                            backgroundColor = Color.Black,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        )
                        Text(
                            text = expression.name.lowercase(),
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * One tab. Tapping it previews the style; the check mark applies it.
 *
 * Preview and apply are separate on purpose: browsing the other style should not
 * silently change the face the user goes back to.
 */
@Composable
private fun StyleTab(
    style: FaceStyle,
    selected: Boolean,
    inUse: Boolean,
    onClick: () -> Unit,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalOpenDroidColors.current
    val border = if (selected) colors.accentCyan else colors.borderColor

    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = style.label,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (inUse) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.gallery_in_use),
                tint = colors.accentNeonGreen,
                modifier = Modifier.height(16.dp),
            )
        } else {
            Text(
                text = stringResource(R.string.gallery_use),
                color = colors.accentCyan,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onUse),
            )
        }
    }
}




