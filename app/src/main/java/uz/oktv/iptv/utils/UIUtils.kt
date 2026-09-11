package uz.oktv.iptv.utils

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

fun Modifier.denimDoubleBorder(
    outerRadius: Float = 8f,
    innerRadius: Float = 6f
): Modifier = this
    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(outerRadius.dp))
    .padding(2.dp)
    .border(1.dp, Color(0xFF2563EB).copy(alpha = 0.5f), RoundedCornerShape(innerRadius.dp))
