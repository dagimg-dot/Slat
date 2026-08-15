package com.dagimg.glide.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.dagimg.glide.appContainer
import com.dagimg.glide.data.ClipboardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Accessibility Service that provides reliable clipboard monitoring on Android 10+.
 *
 * On Android 10 and later, apps cannot read clipboard content in the background
 * unless they are an Input Method Editor (IME) or have an active accessibility service.
 * This service enables background clipboard monitoring.
 */
class GlideAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "GlideAccessibilityService"

        @Volatile
        private var instance: GlideAccessibilityService? = null

        /**
         * Check if the accessibility service is currently running
         */
        fun isRunning(): Boolean = instance != null

        /**
         * Get the current instance of the accessibility service
         */
        fun getInstance(): GlideAccessibilityService? = instance
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: ClipboardRepository
    private lateinit var clipboardManager: ClipboardManager

    private var lastClipText: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AccessibilityService onCreate")
        instance = this

        repository = appContainer.clipboardRepository
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "AccessibilityService connected")

        val prefs = getSharedPreferences("glide_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("service_enabled", false)

        if (isEnabled) {
            ClipboardService.start(this)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            checkClipboard()
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        Log.d(TAG, "AccessibilityService onDestroy")
        instance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun checkClipboard() {
        try {
            val clip = clipboardManager.primaryClip ?: return
            if (clip.itemCount == 0) return

            val item = clip.getItemAt(0)
            val uri = item.uri
            val text = item.text?.toString() ?: item.coerceToText(this).toString()

            if (repository.shouldIgnore(text, uri?.toString())) return

            val isSensitive =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true
                } else {
                    false
                }

            if (uri != null) {
                val mimeType = contentResolver.getType(uri) ?: clip.description.getMimeType(0)
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
                                Log.d(TAG, "Captured image via accessibility")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not process image stream in accessibility: ${e.message}")
                        }
                    }
                    return
                }
            }

            if (text.isNotBlank() && text != lastClipText) {
                lastClipText = text
                serviceScope.launch {
                    repository.addText(text = text, isSensitive = isSensitive)
                    Log.d(TAG, "Captured text via accessibility: ${text.take(50)}...")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking clipboard in accessibility", e)
        }
    }

    /**
     * Open accessibility settings to enable this service
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}
