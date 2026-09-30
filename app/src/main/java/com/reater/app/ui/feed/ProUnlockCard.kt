package com.reater.app.ui.feed

import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reater.app.ui.components.ProCopy

@Composable
fun ProUnlockCard(
    onUnlockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 依據深色模式調整卡片背景、邊框與文字顏色，避免深色模式下背景過亮或對比不足
    val isDark = MaterialTheme.colorScheme.surface.let { surfaceColor ->
        // 計算表面亮度判斷是否為深色主題
        val luminance = (0.299 * surfaceColor.red + 0.587 * surfaceColor.green + 0.114 * surfaceColor.blue)
        luminance < 0.5
    }

    val cardBackground = if (isDark) Color(0xFF1F1A12) else Color(0xFFFFF9E6)
    val cardBorder = if (isDark) Color(0xFF7A6028).copy(alpha = 0.7f) else Color(0xFFFFD54F).copy(alpha = 0.8f)
    val titleColor = if (isDark) Color(0xFFFFD54F) else Color(0xFF5D4037)
    val descColor = if (isDark) Color(0xFFD6C8AC) else Color(0xFF795548)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, cardBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBackground
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = ProCopy.LOCK_TITLE,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = titleColor
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = ProCopy.LOCK_DESC_UNLOCKED_FEATURES,
                fontSize = 13.sp,
                color = descColor,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onUnlockClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color(0xFFFFB300) else MaterialTheme.colorScheme.primary,
                    contentColor = if (isDark) Color.Black else Color.White
                )
            ) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(ProCopy.LOCK_CTA, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
