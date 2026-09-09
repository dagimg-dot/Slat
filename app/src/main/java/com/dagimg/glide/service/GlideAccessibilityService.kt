package com.dagimg.glide.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.dagimg.glide.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class GlideAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "GlideAccessibility"
        const val PREF_AUTO_CAPTURE = "auto_capture_enabled"

        @Volatile
        private var instance: GlideAccessibilityService? = null

        @Volatile
        var currentForegroundApp: String? = null
            private set

        private val IGNORED_PACKAGES =
            setOf(
                "com.android.systemui",
                "android",
                "com.dagimg.glide",
                "com.dagimg.glide.dev",
            )

        private val appNameCache = ConcurrentHashMap<String, String>()

        fun isRunning(): Boolean = instance != null

        fun getInstance(): GlideAccessibilityService? = instance

        fun getAppLabel(
            context: Context,
            packageName: String,
        ): String {
            return appNameCache.getOrPut(packageName) {
                try {
                    val pm = context.packageManager
                    val info: ApplicationInfo =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getApplicationInfo(packageName, 0)
                        }
                    pm.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                }
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var overlayReader: TransientFocusOverlayReader? = null
    private var copyDetector: ClipboardCopyDetector? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        overlayReader =
            TransientFocusOverlayReader(this) { clip, sourceApp ->
                handleCapturedClip(clip, sourceApp)
            }

        copyDetector =
            ClipboardCopyDetector { sourceApp ->
                onCopyDetected(sourceApp)
            }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        val prefs = getSharedPreferences("glide_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("service_enabled", false)

        if (isEnabled) {
            ClipboardService.start(this)
        }
    }

    private fun isAutoCaptureEnabled(): Boolean {
        val prefs = getSharedPreferences("glide_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_AUTO_CAPTURE, true)
    }

    fun onPrimaryClipChanged() {
        if (!isAutoCaptureEnabled()) return
        copyDetector?.onPrimaryClipChanged(currentForegroundApp)
    }

    private fun onCopyDetected(sourceApp: String?) {
        if (!isAutoCaptureEnabled()) return

        if (ClipboardService.instance?.isPanelOpen() == true) {
            return
        }

        overlayReader?.requestCapture(sourceApp)
    }

    private fun handleCapturedClip(
        clip: ClipData,
        sourceApp: String?,
    ) {
        val service = ClipboardService.instance
        if (service != null) {
            service.processClipData(clip, sourceApp)
        } else {
            val repository = appContainer.clipboardRepository
            if (clip.itemCount == 0) return
            val item = clip.getItemAt(0)
            val uri = item.uri
            val text = item.text?.toString() ?: item.coerceToText(this).toString()

            val isSensitive =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true
                } else {
                    false
                }

            if (uri != null) {
                val mimeType = contentResolver.getType(uri) ?: clip.description.getMimeType(0)
                if (mimeType?.startsWith("image/") == true) {
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            repository.addImageFromStream(
                                uri = uri.toString(),
                                sourceApp = sourceApp,
                                isSensitive = isSensitive,
                                openStream = { contentResolver.openInputStream(uri) },
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to stream image: ${e.message}")
                        }
                    }
                    return
                }
            }

            if (text.isNotBlank()) {
                serviceScope.launch(Dispatchers.IO) {
                    repository.addText(
                        text = text,
                        sourceApp = sourceApp,
                        isSensitive = isSensitive,
                    )
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && packageName != null) {
            if (!IGNORED_PACKAGES.contains(packageName) && !packageName.startsWith("com.dagimg.glide")) {
                currentForegroundApp = packageName
            }
        }

        copyDetector?.processAccessibilityEvent(event, currentForegroundApp)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        overlayReader?.destroy()
        overlayReader = null
        copyDetector = null
        serviceScope.cancel()
        super.onDestroy()
    }

    fun openAccessibilitySettings(context: Context) {
        val intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        context.startActivity(intent)
    }
}
