package com.dailyschedule.app.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 数字键盘。3 列 × 4 行：1-9、.、0、删除。 */
@Composable
fun DsNumberPad(
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keys =
        listOf(
            "1", "2", "3",
            "4", "5", "6",
            "7", "8", "9",
            ".", "0", "del",
        )
    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        keys.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { key ->
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .aspectRatio(1.6f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable { onKey(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (key == "del") "⌫" else key,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
