package com.dagimg.glide.ui.overlay

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.dagimg.glide.data.ClipboardEntity
import com.dagimg.glide.service.GlideAccessibilityService
import com.dagimg.glide.ui.theme.AccentPrimary
import com.dagimg.glide.ui.theme.AccentWarning
import com.dagimg.glide.ui.theme.SurfaceContainerDark
import com.dagimg.glide.ui.theme.SurfaceContainerHighDark
import com.dagimg.glide.ui.theme.TextMuted
import com.dagimg.glide.ui.theme.TextPrimary
import com.dagimg.glide.ui.theme.TextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val dateFormatter = SimpleDateFormat("MMM d", Locale.getDefault())

/**
 * Individual clipboard item card supporting text, images, sensitive content, and pin states.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipboardItemCard(
    item: ClipboardEntity,
    isRevealed: Boolean,
    onToggleReveal: () -> Unit,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true },
                ),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = if (item.isPinned) SurfaceContainerHighDark else SurfaceContainerDark,
            ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.isPinned) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(15.dp),
                            tint = AccentPrimary,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    if (item.isSensitive) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Sensitive",
                            modifier = Modifier.size(15.dp),
                            tint = AccentWarning,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    item.sourceApp?.let { app ->
                        val cleanAppName =
                            remember(app) {
                                GlideAccessibilityService.getAppLabel(context, app)
                            }
                        Text(
                            text = cleanAppName,
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                val timeString = remember(item.timestamp) { formatRelativeTime(item.timestamp) }
                Text(
                    text = timeString,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (item.isImage && item.imagePath != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val imageRequest =
                        remember(item.imagePath) {
                            ImageRequest.Builder(context)
                                .data(File(item.imagePath))
                                .size(Size(140, 140))
                                .precision(Precision.EXACT)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .crossfade(false)
                                .build()
                        }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = "Clipboard image",
                        modifier =
                            Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        tint = TextSecondary,
                    )
                }
            } else {
                val displayText =
                    if (item.isSensitive && !isRevealed) {
                        "••••••••••••••••"
                    } else {
                        item.text ?: ""
                    }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = displayText,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )

                    if (item.isSensitive) {
                        IconButton(
                            onClick = onToggleReveal,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isRevealed) "Hide" else "Reveal",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(if (item.isPinned) "Unpin" else "Pin") },
                onClick = {
                    showMenu = false
                    onPin()
                },
                leadingIcon = {
                    Icon(Icons.Default.PushPin, contentDescription = null)
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(Icons.Default.Delete, contentDescription = null)
                },
            )
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
        diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
        else -> synchronized(dateFormatter) { dateFormatter.format(Date(timestamp)) }
    }
}
