package com.dagimg.glide.overlay

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import com.dagimg.glide.data.ClipboardEntity
import com.dagimg.glide.data.ClipboardRepository
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Clipboard panel overlay that shows clipboard history.
 * Uses Jetpack Compose for the UI embedded in an overlay window.
 */
@SuppressLint("ViewConstructor")
class ClipboardPanelView(
    context: Context,
    private val repository: ClipboardRepository,
    private val onSettingsClick: () -> Unit,
    private val onClose: () -> Unit,
) : FrameLayout(context),
    LifecycleOwner,
    SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        // Set background with rounded left corners only (panel slides from right edge)
        val cornerRadiusPx = 48f
        background =
            GradientDrawable().apply {
                // Semi-transparent dark background
                setColor(Color.parseColor("#EE121212"))
                cornerRadii =
                    floatArrayOf(
                        cornerRadiusPx,
                        cornerRadiusPx, // Top-left
                        0f,
                        0f, // Top-right (flush with edge)
                        0f,
                        0f, // Bottom-right (flush with edge)
                        cornerRadiusPx,
                        cornerRadiusPx, // Bottom-left
                    )
            }

        // Clip to the rounded corners
        clipToOutline = true
        outlineProvider =
            object : android.view.ViewOutlineProvider() {
                override fun getOutline(
                    view: android.view.View,
                    outline: android.graphics.Outline,
                ) {
                    // Only round left corners
                    outline.setRoundRect(
                        0,
                        0,
                        view.width + cornerRadiusPx.toInt(),
                        view.height,
                        cornerRadiusPx,
                    )
                }
            }

        // Setup lifecycle for Compose
        setViewTreeLifecycleOwner(this)
        setViewTreeSavedStateRegistryOwner(this)

        // Add ComposeView
        val composeView =
            ComposeView(context).apply {
                setContent {
                    clipboardPanelContent(
                        repository = repository,
                        onItemClick = { item -> copyToClipboard(item) },
                        onItemPin = { item -> togglePin(item) },
                        onItemDelete = { item -> deleteItem(item) },
                        onSettingsClick = onSettingsClick,
                    )
                }
            }

        // Add gesture detector for swipe to close
        val gestureDetector =
            android.view.GestureDetector(
                context,
                object : android.view.GestureDetector.SimpleOnGestureListener() {
                    override fun onFling(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float,
                    ): Boolean {
                        if (e1 != null && e2.rawX > e1.rawX && velocityX > 1000) {
                            onClose()
                            return true
                        }
                        return false
                    }
                },
            )

        composeView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false // Let Compose handle other touches
        }

        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Handle back button to close panel
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            onClose()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun copyToClipboard(item: ClipboardEntity) {
        performHapticFeedback()

        if (item.isImage && item.imagePath != null) {
            try {
                val file = File(item.imagePath)
                if (file.exists()) {
                    val uri =
                        androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )

                    val clip = ClipData.newUri(context.contentResolver, "Image", uri)
                    clipboardManager.setPrimaryClip(clip)
                }
            } catch (e: Exception) {
                // Fallback to text if file provider fails
                val clip = ClipData.newPlainText("Glide", "[Image Missing]")
                clipboardManager.setPrimaryClip(clip)
            }
        } else if (item.text != null) {
            val clip = ClipData.newPlainText("Glide", item.text)
            clipboardManager.setPrimaryClip(clip)
        }

        // Close panel after copying
        onClose()
    }

    private fun togglePin(item: ClipboardEntity) {
        performHapticFeedback()
        kotlinx.coroutines.GlobalScope.launch {
            repository.togglePin(item.id)
        }
    }

    private fun deleteItem(item: ClipboardEntity) {
        performHapticFeedback()
        kotlinx.coroutines.GlobalScope.launch {
            repository.delete(item)
        }
    }

    private fun performHapticFeedback() {
        vibrator?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(30)
            }
        }
    }
}

/**
 * Compose content for the clipboard panel
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun clipboardPanelContent(
    repository: ClipboardRepository,
    onItemClick: (ClipboardEntity) -> Unit,
    onItemPin: (ClipboardEntity) -> Unit,
    onItemDelete: (ClipboardEntity) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val items by repository.getAllItems().collectAsState(initial = emptyList())

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    androidx.compose.ui.graphics
                                        .Color(0xFF1A1A1A),
                                    androidx.compose.ui.graphics
                                        .Color(0xFF0D0D0D),
                                ),
                        ),
                ).padding(16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Clipboard",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.White,
            )

            Row {
                // Settings button
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (items.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = androidx.compose.ui.graphics.Color.Gray,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No clipboard history",
                        color = androidx.compose.ui.graphics.Color.Gray,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = "Copy something to see it here",
                        color = androidx.compose.ui.graphics.Color.DarkGray,
                        fontSize = 14.sp,
                    )
                }
            }
        } else {
            // Clipboard items list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    clipboardItemCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        onPin = { onItemPin(item) },
                        onDelete = { onItemDelete(item) },
                    )
                }
            }
        }
    }
}

/**
 * Individual clipboard item card
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun clipboardItemCard(
    item: ClipboardEntity,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true },
                ),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (item.isPinned) {
                        androidx.compose.ui.graphics
                            .Color(0xFF2A2A2A)
                    } else {
                        androidx.compose.ui.graphics
                            .Color(0xFF1E1E1E)
                    },
            ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            // Pin indicator and timestamp row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.isPinned) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(14.dp),
                            tint =
                                androidx.compose.ui.graphics
                                    .Color(0xFF6C5CE7),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Pinned",
                            fontSize = 12.sp,
                            color =
                                androidx.compose.ui.graphics
                                    .Color(0xFF6C5CE7),
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Text(
                    text = formatRelativeTime(item.timestamp),
                    fontSize = 12.sp,
                    color = androidx.compose.ui.graphics.Color.Gray,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Content
            if (item.isImage && item.imagePath != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = File(item.imagePath),
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
                        tint = androidx.compose.ui.graphics.Color.Gray,
                    )
                }
            } else {
                Text(
                    text = item.text ?: "",
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
        }

        // Context menu
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

/**
 * Format timestamp as relative time (e.g., "2m ago", "1h ago")
 */
private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
        diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
