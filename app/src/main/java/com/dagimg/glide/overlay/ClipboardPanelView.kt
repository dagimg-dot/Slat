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
import android.widget.FrameLayout
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.platform.LocalContext
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
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.dagimg.glide.data.ClipboardEntity
import com.dagimg.glide.data.ClipboardRepository
import com.dagimg.glide.ui.theme.AccentPrimary
import com.dagimg.glide.ui.theme.AccentWarning
import com.dagimg.glide.ui.theme.SurfaceContainerDark
import com.dagimg.glide.ui.theme.SurfaceContainerHighDark
import com.dagimg.glide.ui.theme.SurfaceContainerLowDark
import com.dagimg.glide.ui.theme.TextMuted
import com.dagimg.glide.ui.theme.TextPrimary
import com.dagimg.glide.ui.theme.TextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val dateFormatter = SimpleDateFormat("MMM d", Locale.getDefault())

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
    private val viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        val cornerRadiusPx = 48f
        background =
            GradientDrawable().apply {
                setColor(Color.parseColor("#EE121212"))
                cornerRadii =
                    floatArrayOf(
                        cornerRadiusPx,
                        cornerRadiusPx,
                        0f,
                        0f,
                        0f,
                        0f,
                        cornerRadiusPx,
                        cornerRadiusPx,
                    )
            }

        clipToOutline = true
        outlineProvider =
            object : android.view.ViewOutlineProvider() {
                override fun getOutline(
                    view: android.view.View,
                    outline: android.graphics.Outline,
                ) {
                    outline.setRoundRect(
                        0,
                        0,
                        view.width + cornerRadiusPx.toInt(),
                        view.height,
                        cornerRadiusPx,
                    )
                }
            }

        setViewTreeLifecycleOwner(this)
        setViewTreeSavedStateRegistryOwner(this)

        val composeView =
            ComposeView(context).apply {
                setContent {
                    ClipboardPanelContent(
                        repository = repository,
                        onItemClick = { item -> copyToClipboard(item) },
                        onItemPin = { item -> togglePin(item) },
                        onItemDelete = { item -> deleteItem(item) },
                        onSettingsClick = onSettingsClick,
                    )
                }
            }

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
            false
        }

        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun onPanelOpened() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onPanelClosed() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewScope.cancel()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
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
            } catch (_: Exception) {
                val clip = ClipData.newPlainText("Glide", "[Image Missing]")
                clipboardManager.setPrimaryClip(clip)
            }
        } else if (item.text != null) {
            val clip = ClipData.newPlainText("Glide", item.text)
            clipboardManager.setPrimaryClip(clip)
        }

        onClose()
    }

    private fun togglePin(item: ClipboardEntity) {
        performHapticFeedback()
        viewScope.launch(Dispatchers.IO) {
            repository.togglePin(item.id)
        }
    }

    private fun deleteItem(item: ClipboardEntity) {
        performHapticFeedback()
        viewScope.launch(Dispatchers.IO) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipboardPanelContent(
    repository: ClipboardRepository,
    onItemClick: (ClipboardEntity) -> Unit,
    onItemPin: (ClipboardEntity) -> Unit,
    onItemDelete: (ClipboardEntity) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val itemsFlow =
        remember(repository) {
            repository.getAllItems().map { ClipboardUiState(items = it) }
        }
    val uiState by itemsFlow.collectAsState(initial = ClipboardUiState(isLoading = true))
    var revealedIds by remember { mutableStateOf(setOf<String>()) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(SurfaceContainerHighDark, SurfaceContainerLowDark),
                        ),
                ).padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Clipboard",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )

            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.items.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
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
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = uiState.items,
                    key = { it.id },
                    contentType = { if (it.isImage) 1 else 0 },
                ) { item ->
                    val isRevealed = revealedIds.contains(item.id)
                    ClipboardItemCard(
                        item = item,
                        isRevealed = isRevealed,
                        onToggleReveal = {
                            revealedIds =
                                if (isRevealed) {
                                    revealedIds - item.id
                                } else {
                                    revealedIds + item.id
                                }
                        },
                        onClick = { onItemClick(item) },
                        onPin = { onItemPin(item) },
                        onDelete = { onItemDelete(item) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipboardItemCard(
    item: ClipboardEntity,
    isRevealed: Boolean,
    onToggleReveal: () -> Unit,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isPinned) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(14.dp),
                            tint = AccentPrimary,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Pinned",
                            fontSize = 12.sp,
                            color = AccentPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (item.isSensitive) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Sensitive",
                            modifier = Modifier.size(14.dp),
                            tint = AccentWarning,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Sensitive",
                            fontSize = 12.sp,
                            color = AccentWarning,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    item.sourceApp?.let { app ->
                        val cleanAppName =
                            remember(app) {
                                app.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                            }
                        Text(
                            text = cleanAppName,
                            fontSize = 11.sp,
                            color = TextMuted,
                        )
                    }
                }

                val timeString = remember(item.timestamp) { formatRelativeTime(item.timestamp) }
                Text(
                    text = timeString,
                    fontSize = 12.sp,
                    color = TextSecondary,
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
