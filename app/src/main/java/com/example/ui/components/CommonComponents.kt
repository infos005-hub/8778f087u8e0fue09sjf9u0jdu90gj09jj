package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.*

@Composable
fun TagChip(
    text: String,
    isHighlighted: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val bg = if (isHighlighted) AnonSecondary.copy(alpha = 0.2f) else AnonSurfaceElevated
    val border = if (isHighlighted) AnonSecondary.copy(alpha = 0.6f) else AnonSurfaceBorder
    val textColor = if (isHighlighted) AnonSecondary else AnonTextSecondary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = "#$text",
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun HeartBalanceBadge(
    balance: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = AnonPrimary.copy(alpha = 0.10f),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AnonPrimary.copy(alpha = 0.25f)),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "하트",
                tint = AnonPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$balance",
                color = AnonTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
