package com.dagimg.glide.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dagimg.glide.MainActivity
import com.dagimg.glide.R
import com.dagimg.glide.appContainer
import com.dagimg.glide.data.ClipboardRepository
import com.dagimg.glide.overlay.ClipboardPanelView
import com.dagimg.glide.overlay.EdgeHandleView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ClipboardService : Service() {
    companion object {
        private const val TAG = "ClipboardService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "clipboard_monitor_channel"
        private const val PREF_HANDLE_Y = "handle_y_position"
        private const val PANEL_WIDTH_PERCENT = 0.45
        private const val PANEL_HEIGHT_PERCENT = 0.80

        var instance: ClipboardService? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, ClipboardService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ClipboardService::class.java))
        }

        fun isRunning(context: Context): Boolean {
            val prefs = context.getSharedPreferences("glide_prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean("service_enabled", false)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: ClipboardRepository
    private lateinit var windowManager: WindowManager
    private lateinit var clipboardManager: ClipboardManager

    private var edgeHandle: EdgeHandleView? = null
    private var edgeHandleParams: WindowManager.LayoutParams? = null

    private var overlayContainer: FrameLayout? = null
    private var overlayContainerParams: WindowManager.LayoutParams? = null
    private var clipboardPanel: ClipboardPanelView? = null
    private var scrimView: View? = null

    private val clipboardListener =
        ClipboardManager.OnPrimaryClipChangedListener {
            handleClipboardChange()
        }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        instance = this

        repository = appContainer.clipboardRepository
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboardManager.addPrimaryClipChangedListener(clipboardListener)

        createEdgeHandle()
        createOverlayContainer()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Log.d(TAG, "Service onStartCommand")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        getSharedPreferences("glide_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("service_enabled", true)
            .apply()

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        instance = null

        clipboardManager.removePrimaryClipChangedListener(clipboardListener)

        removeEdgeHandle()
        removeOverlayContainer()

        getSharedPreferences("glide_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("service_enabled", false)
            .apply()

        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun checkAndCaptureClipboard(sourceApp: String? = null) {
        serviceScope.launch(Dispatchers.Main) {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                handleClipboardChange(clip, sourceApp)
            }
        }
    }

    private fun handleClipboardChange(
        clip: ClipData? = null,
        sourceApp: String? = null,
    ) {
        val activeClip = clip ?: clipboardManager.primaryClip ?: return
        if (activeClip.itemCount == 0) return

        val item = activeClip.getItemAt(0)
        val uri = item.uri
        val text = item.text?.toString() ?: item.coerceToText(this@ClipboardService).toString()

        if (repository.shouldIgnore(text, uri?.toString())) return

        val isSensitive =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activeClip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true
            } else {
                false
            }

        if (uri != null) {
            val description = activeClip.description
            val mimeType = contentResolver.getType(uri) ?: description.getMimeType(0)

            if (mimeType?.startsWith("image/") == true) {
                serviceScope.launch {
                    try {
                        val added =
                            repository.addImageFromStream(
                                uri = uri.toString(),
                                sourceApp = sourceApp,
                                isSensitive = isSensitive,
                                openStream = { contentResolver.openInputStream(uri) },
                            )
                        if (added) {
                            Log.d(TAG, "Captured image successfully from $sourceApp")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not process image stream: ${e.message}")
                    }
                }
                return
            }
        }

        if (text.isNotBlank()) {
            serviceScope.launch {
                val added =
                    repository.addText(
                        text = text,
                        sourceApp = sourceApp,
                        isSensitive = isSensitive,
                    )
                if (added) {
                    Log.d(TAG, "Captured text from $sourceApp: ${text.take(50)}...")
                }
            }
        }
    }

    private fun createEdgeHandle() {
        val displayMetrics = resources.displayMetrics
        val handleWidth = (18 * displayMetrics.density).toInt()
        val handleHeight = (80 * displayMetrics.density).toInt()
        val maxY = (displayMetrics.heightPixels - handleHeight).coerceAtLeast(0)
        val defaultY = (displayMetrics.heightPixels * 0.35f).toInt()

        val savedY =
            getSharedPreferences("glide_prefs", Context.MODE_PRIVATE)
                .getInt(PREF_HANDLE_Y, -1)

        val initialY = if (savedY in 0..maxY) savedY else defaultY

        edgeHandle =
            EdgeHandleView(
                context = this,
                onTap = { togglePanel() },
                onDrag = { deltaY -> updateHandlePosition(deltaY) },
                onDragEnd = { saveHandlePosition() },
            )

        edgeHandleParams =
            WindowManager
                .LayoutParams(
                    handleWidth,
                    handleHeight,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    x = 0
                    y = initialY
                }

        try {
            windowManager.addView(edgeHandle, edgeHandleParams)
            Log.d(TAG, "Edge handle added successfully at y=$initialY")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add edge handle", e)
        }
    }

    private fun updateHandlePosition(deltaY: Float) {
        edgeHandleParams?.let { params ->
            val displayMetrics = resources.displayMetrics
            val handleHeight = (80 * displayMetrics.density).toInt()
            val maxY = (displayMetrics.heightPixels - handleHeight).coerceAtLeast(0)
            params.y = (params.y + deltaY.toInt()).coerceIn(0, maxY)
            try {
                windowManager.updateViewLayout(edgeHandle, params)
            } catch (_: Exception) {
            }
        }
    }

    private fun saveHandlePosition() {
        edgeHandleParams?.let { params ->
            getSharedPreferences("glide_prefs", Context.MODE_PRIVATE)
                .edit()
                .putInt(PREF_HANDLE_Y, params.y)
                .apply()
            Log.d(TAG, "Saved handle position: y=${params.y}")
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createOverlayContainer() {
        overlayContainer = FrameLayout(this)

        scrimView =
            View(this).apply {
                setBackgroundColor(Color.parseColor("#66000000"))
                alpha = 0f
                setOnTouchListener { _, event ->
                    if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                        hidePanel()
                        true
                    } else {
                        false
                    }
                }
            }

        clipboardPanel =
            ClipboardPanelView(
                context = this,
                repository = repository,
                onSettingsClick = {
                    val intent =
                        Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    startActivity(intent)
                    hidePanel()
                },
                onClose = { hidePanel() },
            ).apply {
                onFocusGained = {
                    Log.d(TAG, "ClipboardPanelView gained window focus, capturing clipboard...")
                    handleClipboardChange(sourceApp = GlideAccessibilityService.currentForegroundApp)
                }
            }

        overlayContainer?.let { container ->
            container.isFocusable = true
            container.isFocusableInTouchMode = true
            container.setViewTreeLifecycleOwner(clipboardPanel)
            container.setViewTreeSavedStateRegistryOwner(clipboardPanel)
        }

        val displayMetrics = resources.displayMetrics
        val panelWidth = (displayMetrics.widthPixels * PANEL_WIDTH_PERCENT).toInt()
        val panelHeight = (displayMetrics.heightPixels * PANEL_HEIGHT_PERCENT).toInt()

        val panelParams =
            FrameLayout.LayoutParams(panelWidth, panelHeight).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }

        overlayContainer?.addView(scrimView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        overlayContainer?.addView(clipboardPanel, panelParams)

        overlayContainer?.visibility = View.GONE
        clipboardPanel?.translationX = panelWidth.toFloat()

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            )
        overlayContainerParams = params

        try {
            windowManager.addView(overlayContainer, params)
            Log.d(TAG, "Overlay container added")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay container", e)
        }
    }

    private fun togglePanel() {
        Log.d(TAG, "togglePanel called, current visibility=${overlayContainer?.visibility}")
        if (overlayContainer?.visibility == View.VISIBLE) {
            hidePanel()
        } else {
            showPanel()
        }
    }

    fun showPanel() {
        Log.d(TAG, "showPanel called")
        if (overlayContainer?.visibility == View.VISIBLE) return

        overlayContainerParams?.let { params ->
            params.flags =
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            try {
                windowManager.updateViewLayout(overlayContainer, params)
            } catch (e: Exception) {
                Log.w(TAG, "Error updating overlay layout params: ${e.message}")
            }
        }

        val displayMetrics = resources.displayMetrics
        val panelWidth = (displayMetrics.widthPixels * PANEL_WIDTH_PERCENT).toFloat()

        overlayContainer?.visibility = View.VISIBLE
        edgeHandle?.visibility = View.GONE
        clipboardPanel?.onPanelOpened()

        overlayContainer?.requestFocus()
        clipboardPanel?.requestFocus()

        handleClipboardChange(sourceApp = GlideAccessibilityService.currentForegroundApp)

        clipboardPanel?.postDelayed({
            handleClipboardChange(sourceApp = GlideAccessibilityService.currentForegroundApp)
        }, 100)

        scrimView
            ?.animate()
            ?.alpha(1f)
            ?.setDuration(250)
            ?.start()

        clipboardPanel?.let { panel ->
            panel.translationX = if (panel.width > 0) panel.width.toFloat() else panelWidth
            panel
                .animate()
                .translationX(0f)
                .setDuration(250)
                .start()
        }
    }

    fun hidePanel() {
        Log.d(TAG, "hidePanel called")
        if (overlayContainer?.visibility != View.VISIBLE) return

        val displayMetrics = resources.displayMetrics
        val panelWidth = (displayMetrics.widthPixels * PANEL_WIDTH_PERCENT).toFloat()

        scrimView
            ?.animate()
            ?.alpha(0f)
            ?.setDuration(200)
            ?.start()

        clipboardPanel?.let { panel ->
            val targetX = if (panel.width > 0) panel.width.toFloat() else panelWidth
            panel
                .animate()
                .translationX(targetX)
                .setDuration(200)
                .withEndAction {
                    overlayContainer?.visibility = View.GONE
                    edgeHandle?.visibility = View.VISIBLE
                    clipboardPanel?.onPanelClosed()

                    overlayContainerParams?.let { params ->
                        params.flags =
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        try {
                            windowManager.updateViewLayout(overlayContainer, params)
                        } catch (_: Exception) {
                        }
                    }
                }.start()
        }
    }

    private fun removeOverlayContainer() {
        overlayContainer?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayContainer = null
        scrimView = null
        clipboardPanel = null
    }

    private fun removeEdgeHandle() {
        edgeHandle?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        edgeHandle = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Clipboard Monitor",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Keeps Glide running in the background to monitor clipboard"
                    setShowBadge(false)
                }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Glide is active")
            .setContentText("Monitoring clipboard in background")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
