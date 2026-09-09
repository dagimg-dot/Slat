package com.dagimg.glide.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class GlideAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "GlideAccessibility"

        @Volatile
        private var instance: GlideAccessibilityService? = null

        @Volatile
        var currentForegroundApp: String? = null
            private set

        fun isRunning(): Boolean = instance != null

        fun getInstance(): GlideAccessibilityService? = instance
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
        val packageName = event?.packageName?.toString()
        if (packageName != null && !packageName.startsWith("com.dagimg.glide")) {
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
