// The one prominent control on the chat screen: enter hands-free mode.
//
// The label says "Hands-free", not "Auto": the top bar already has an "Auto"
// chip that means automatic plan approval, and two controls named Auto on one
// screen would read as the same setting.

package com.opendroid.ai.ui.face

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opendroid.ai.ui.theme.LocalOpenDroidColors

@Composable
fun AutoModeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalOpenDroidColors.current
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, colors.accentCyan),
        modifier = modifier.height(36.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = colors.accentCyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Hands-free",
                color = colors.accentCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
