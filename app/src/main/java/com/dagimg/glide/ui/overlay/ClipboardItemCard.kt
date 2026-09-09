package com.dagimg.glide.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipboardItemCard(
    item: ClipboardEntity,
    isRevealed: Boolean,
    showActions: Boolean,
    onToggleReveal: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissActions: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (showActions) {
                                onDismissActions()
                            } else {
                                onClick()
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick()
                        },
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
        }

        AnimatedVisibility(
            visible = showActions,
            enter =
                fadeIn(tween(140)) +
                    scaleIn(
                        initialScale = 0.95f,
                        animationSpec = tween(140, easing = FastOutSlowInEasing),
                    ),
            exit =
                fadeOut(tween(100)) +
                    scaleOut(
                        targetScale = 0.95f,
                        animationSpec = tween(100),
                    ),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xF2131316))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onDismissActions()
                        },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Surface(
                        onClick = {
                            onDismissActions()
                            onPin()
                        },
                        shape = CircleShape,
                        color = if (item.isPinned) Color(0xFF1E293B) else Color(0xFF22222E),
                        border =
                            BorderStroke(
                                1.dp,
                                if (item.isPinned) Color(0xFF38BDF8).copy(alpha = 0.6f) else Color(0xFF818CF8).copy(alpha = 0.5f),
                            ),
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = if (item.isPinned) "Unpin" else "Pin",
                                tint = if (item.isPinned) Color(0xFF38BDF8) else Color(0xFF818CF8),
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            onDismissActions()
                            onDelete()
                        },
                        shape = CircleShape,
                        color = Color(0xFF2B1616),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
            }
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
