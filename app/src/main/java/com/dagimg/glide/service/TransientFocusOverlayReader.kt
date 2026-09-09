package com.dagimg.glide.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

class TransientFocusOverlayReader(
    private val context: Context,
    private val onClipCaptured: (ClipData, String?) -> Unit,
) {
    companion object {
        private const val TAG = "TransientOverlayReader"
        private const val WATCHDOG_TIMEOUT_MS = 400L
        private const val COOLDOWN_MS = 300L
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val captureMutex = Mutex()
    private val isAttached = AtomicBoolean(false)

    @Volatile private var lastProcessedTimestamp: Long = 0L

    @Volatile private var lastCaptureTime: Long = 0L
    private var dummyView: FocusDummyView? = null
    private var watchdogJob: Job? = null
    private var pendingSourceApp: String? = null

    private inner class FocusDummyView(context: Context) : View(context) {
        init {
            isFocusable = true
            isFocusableInTouchMode = true
        }

        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            if (hasWindowFocus) {
                mainHandler.post {
                    processClipboardRead()
                }
            }
        }
    }

    fun requestCapture(sourceApp: String? = null) {
        if (!powerManager.isInteractive) return

        val now = System.currentTimeMillis()
        if (now - lastCaptureTime < COOLDOWN_MS && isAttached.get()) {
            return
        }

        scope.launch {
            captureMutex.withLock {
                if (isAttached.get()) return@withLock

                pendingSourceApp = sourceApp
                lastCaptureTime = System.currentTimeMillis()
                attachDummyView()
            }
        }
    }

    private fun attachDummyView() {
        try {
            val view = FocusDummyView(context)
            dummyView = view

            val params =
                WindowManager.LayoutParams(
                    1,
                    1,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = 0
                    y = 0
                }

            windowManager.addView(view, params)
            isAttached.set(true)
            view.requestFocus()

            watchdogJob?.cancel()
            watchdogJob =
                scope.launch {
                    delay(WATCHDOG_TIMEOUT_MS)
                    if (isAttached.get()) {
                        safeRemoveView()
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach transient overlay", e)
            isAttached.set(false)
            dummyView = null
        }
    }

    private fun processClipboardRead() {
        if (!isAttached.get()) return

        try {
            val description = clipboardManager.primaryClipDescription
            val clipTimestamp = description?.timestamp ?: 0L

            if (clipTimestamp != 0L && clipTimestamp == lastProcessedTimestamp) {
                safeRemoveView()
                return
            }

            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                lastProcessedTimestamp = if (clipTimestamp != 0L) clipTimestamp else System.currentTimeMillis()
                val source = pendingSourceApp
                Log.d(TAG, "Captured background clip from $source")
                onClipCaptured(clip, source)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading clipboard", e)
        } finally {
            safeRemoveView()
        }
    }

    private fun safeRemoveView() {
        watchdogJob?.cancel()
        watchdogJob = null

        val view = dummyView ?: return
        if (isAttached.compareAndSet(true, false)) {
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: Exception) {
            } finally {
                dummyView = null
                pendingSourceApp = null
            }
        }
    }

    fun destroy() {
        safeRemoveView()
    }
}
