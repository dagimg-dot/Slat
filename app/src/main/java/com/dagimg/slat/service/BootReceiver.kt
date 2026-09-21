package com.dagimg.slat.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives BOOT_COMPLETED broadcast to restart the clipboard service after device reboot.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("slat_prefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("service_enabled", false)

            if (isEnabled) {
                Log.d(TAG, "Starting ClipboardService after boot")
                ClipboardService.start(context)
            }
        }
    }
}
