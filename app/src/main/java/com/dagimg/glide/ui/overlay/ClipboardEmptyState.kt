package com.dagimg.glide.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dagimg.glide.ui.theme.TextMuted
import com.dagimg.glide.ui.theme.TextSecondary

/**
 * Placeholder view rendered when the clipboard history is empty.
 */
@Composable
fun ClipboardEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = TextMuted,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No clipboard history",
                color = TextSecondary,
                fontSize = 16.sp,
            )
            Text(
                text = "Copy something to see it here",
                color = TextMuted,
                fontSize = 14.sp,
            )
        }
    }
}
