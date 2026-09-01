// The one prominent control on the chat screen: enter hands-free mode.
//
// The label says "Hands-free", not "Auto": the top bar already has an "Auto"
// chip that means automatic plan approval, and two controls named Auto on one
// screen would read as the same setting.

package com.opendroid.ai.ui.face

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import com.opendroid.ai.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendroid.ai.ui.theme.LocalOpenDroidColors

@Composable
fun AutoModeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalOpenDroidColors.current
    // A soft-filled pill rather than a cyan outline. On a near-black screen an
    // outlined accent button glows like a warning; this is the friendliest thing
    // on the chat screen, and it should look like it.
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(colors.accentCyan.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = null,
            tint = colors.accentCyan,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.chat_hands_free),
            color = colors.accentCyan,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

