package com.reater.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dialog 通用頂欄：標題靠左、X 關閉鍵永遠最右，符合操作邏輯。
 */
@Composable
fun DialogHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    titleExtra: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            titleExtra?.invoke()
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "關閉",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
