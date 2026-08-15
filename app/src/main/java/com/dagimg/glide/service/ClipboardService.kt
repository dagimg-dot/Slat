package com.dagimg.glide.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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

/**
 * Foreground service that manages the clipboard listener and overlay views.
 * Runs continuously when enabled to capture clipboard changes and show edge panel.
 */
class ClipboardService : Service() {
    companion object {
        private const val TAG = "ClipboardService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "glide_service_channel"
        private const val PREF_HANDLE_Y = "handle_y_position"
        private const val PANEL_WIDTH_PERCENT = 0.45
        private const val PANEL_HEIGHT_PERCENT = 0.80

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

    // Single container for both scrim and panel
    private var overlayContainer: FrameLayout? = null
    private var clipboardPanel: ClipboardPanelView? = null
    private var scrimView: View? = null

    private val clipboardListener =
        ClipboardManager.OnPrimaryClipChangedListener {
            handleClipboardChange()
        }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

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

    private fun handleClipboardChange() {
        val clip = clipboardManager.primaryClip ?: return
        if (clip.itemCount == 0) return

        val item = clip.getItemAt(0)
        val uri = item.uri
        val text = item.text?.toString() ?: item.coerceToText(this@ClipboardService).toString()

        if (repository.shouldIgnore(text, uri?.toString())) return

        val isSensitive =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true
            } else {
                false
            }

        if (uri != null) {
            val description = clip.description
            val mimeType = contentResolver.getType(uri) ?: description.getMimeType(0)

            if (mimeType?.startsWith("image/") == true) {
                serviceScope.launch {
                    try {
                        val added =
                            repository.addImageFromStream(
                                uri = uri.toString(),
                                isSensitive = isSensitive,
                                openStream = { contentResolver.openInputStream(uri) },
                            )
                        if (added) {
                            Log.d(TAG, "Captured image successfully")
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
                repository.addText(text = text, isSensitive = isSensitive)
                Log.d(TAG, "Captured text: ${text.take(50)}...")
            }
        }
    }

    /**
     * Create the edge handle overlay view with saved Y position
     */
    private fun createEdgeHandle() {
        val savedY =
            getSharedPreferences("glide_prefs", Context.MODE_PRIVATE)
                .getInt(PREF_HANDLE_Y, 0)

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
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    x = 0
                    y = savedY
                }

        try {
            windowManager.addView(edgeHandle, edgeHandleParams)
            Log.d(TAG, "Edge handle added at y=$savedY")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add edge handle", e)
        }
    }

    private fun updateHandlePosition(deltaY: Float) {
        edgeHandleParams?.let { params ->
            params.y += deltaY.toInt()
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

    /**
     * Create a single container holding both scrim and panel
     */
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
            )

        overlayContainer?.let { container ->
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
            WindowManager
                .LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT,
                )

        try {
            windowManager.addView(overlayContainer, params)
            Log.d(TAG, "Overlay container added")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay container", e)
        }
    }

    private fun togglePanel() {
        if (overlayContainer?.visibility == View.VISIBLE) {
            hidePanel()
        } else {
            showPanel()
        }
    }

    fun showPanel() {
        if (overlayContainer?.visibility == View.VISIBLE) return

        overlayContainer?.visibility = View.VISIBLE
        edgeHandle?.visibility = View.GONE
        clipboardPanel?.onPanelOpened()

        scrimView
            ?.animate()
            ?.alpha(1f)
            ?.setDuration(250)
            ?.start()

        clipboardPanel?.let { panel ->
            panel.translationX = panel.width.toFloat()
            panel
                .animate()
                .translationX(0f)
                .setDuration(250)
                .start()
        }
    }

    fun hidePanel() {
        if (overlayContainer?.visibility != View.VISIBLE) return

        scrimView
            ?.animate()
            ?.alpha(0f)
            ?.setDuration(200)
            ?.start()

        clipboardPanel?.let { panel ->
            panel
                .animate()
                .translationX(panel.width.toFloat())
                .setDuration(200)
                .withEndAction {
                    overlayContainer?.visibility = View.GONE
                    edgeHandle?.visibility = View.VISIBLE
                    clipboardPanel?.onPanelClosed()
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
        edgeHandleParams = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = getString(R.string.notification_channel_description)
                    setShowBadge(false)
                }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
