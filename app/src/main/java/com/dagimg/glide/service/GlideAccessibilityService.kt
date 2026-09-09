package com.dagimg.glide.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.ConcurrentHashMap

class GlideAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "GlideAccessibility"

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

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AccessibilityService onCreate")
        instance = this
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
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        if (!IGNORED_PACKAGES.contains(packageName) && !packageName.startsWith("com.dagimg.glide")) {
            currentForegroundApp = packageName
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        Log.d(TAG, "AccessibilityService onDestroy")
        instance = null
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
