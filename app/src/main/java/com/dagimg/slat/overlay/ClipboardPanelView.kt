package com.dagimg.slat.overlay

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dagimg.slat.data.ClipboardEntity
import com.dagimg.slat.data.ClipboardRepository
import com.dagimg.slat.ui.overlay.ClipboardPanelContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Custom overlay FrameLayout hosting the Compose panel and handling gestures and window focus.
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
    companion object {
        private const val TAG = "ClipboardPanelView"
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    var onFocusGained: (() -> Unit)? = null

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        val cornerRadiusPx = 48f
        background =
            GradientDrawable().apply {
                setColor(Color.parseColor("#EE0D0D0D"))
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

        isFocusable = true
        isFocusableInTouchMode = true
        composeView.isFocusable = true
        composeView.isFocusableInTouchMode = true

        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        Log.d(TAG, "onWindowFocusChanged: hasWindowFocus=$hasWindowFocus")
        if (hasWindowFocus) {
            onFocusGained?.invoke()
        }
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
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )

                    val clip = ClipData.newUri(context.contentResolver, "Image", uri)
                    clipboardManager.setPrimaryClip(clip)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to copy image URI to clipboard: ${e.message}")
                val clip = ClipData.newPlainText("Slat", "[Image Missing]")
                clipboardManager.setPrimaryClip(clip)
            }
        } else if (item.text != null) {
            val clip = ClipData.newPlainText("Slat", item.text)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(30)
            }
        }
    }
}
