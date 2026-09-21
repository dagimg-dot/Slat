package com.dagimg.slat.service

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.regex.Pattern

class ClipboardCopyDetector(
    private val onCopyDetected: (sourceApp: String?) -> Unit,
) {
    companion object {
        private const val TAG = "ClipboardCopyDetector"

        private val PASTE_TOAST_BLACKLIST =
            Pattern.compile(
                "(?i).*(pasted|colado|eingefügt|copié depuis|copiato da|wklejono|slat).*",
            )

        private val COPY_ACTION_PATTERN =
            Pattern.compile(
                "(?i)^\\s*(copy|cut|copiar|cortar|kopiuj|kopieren|copier|copia|taglia|📋|✂️)(\\s+to\\s+clipboard)?\\s*$",
            )

        private val COPY_TOAST_PATTERN =
            Pattern.compile(
                "(?i).*(copied to clipboard|copiado al portapapeles|in die zwischenablage kopiert|" +
                    "copié dans le presse-papiers|copiato negli appunti|skopiowano do schowka|" +
                    "copied|copiado|skopiowano).*",
            )
    }

    fun processAccessibilityEvent(
        event: AccessibilityEvent,
        currentForegroundApp: String?,
    ) {
        val packageName = event.packageName?.toString() ?: ""
        if (packageName.startsWith("com.dagimg.slat")) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SELECTED,
            -> {
                checkViewClickEvent(event, currentForegroundApp)
            }

            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                checkNotificationEvent(event, currentForegroundApp)
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                checkWindowStateEvent(event, currentForegroundApp)
            }
        }
    }

    fun onPrimaryClipChanged(currentForegroundApp: String?) {
        Log.d(TAG, "Primary clip changed: app=$currentForegroundApp")
        onCopyDetected(currentForegroundApp)
    }

    private fun checkViewClickEvent(
        event: AccessibilityEvent,
        currentForegroundApp: String?,
    ) {
        val texts = mutableListOf<CharSequence>()
        event.contentDescription?.let { texts.add(it) }
        texts.addAll(event.text)

        val node =
            try {
                event.source
            } catch (_: Exception) {
                null
            }
        node?.let {
            it.text?.let { t -> texts.add(t) }
            it.contentDescription?.let { c -> texts.add(c) }
        }

        for (text in texts) {
            val str = text.toString().trim()
            if (COPY_ACTION_PATTERN.matcher(str).matches() ||
                str.equals("Copy", ignoreCase = true) ||
                str.equals("Cut", ignoreCase = true)
            ) {
                Log.d(TAG, "Copy action clicked ('$str') in $currentForegroundApp")
                onCopyDetected(currentForegroundApp)
                return
            }
        }
    }

    private fun checkNotificationEvent(
        event: AccessibilityEvent,
        currentForegroundApp: String?,
    ) {
        val fullText = event.text.joinToString(" ") { it.toString() }
        if (fullText.isBlank()) return

        if (PASTE_TOAST_BLACKLIST.matcher(fullText).matches()) {
            return
        }

        if (COPY_TOAST_PATTERN.matcher(fullText).matches()) {
            Log.d(TAG, "Copy toast detected ('$fullText') in $currentForegroundApp")
            onCopyDetected(currentForegroundApp)
        }
    }

    private fun checkWindowStateEvent(
        event: AccessibilityEvent,
        currentForegroundApp: String?,
    ) {
        val packageName = event.packageName?.toString() ?: return

        if (packageName == "com.android.systemui") {
            val className = event.className?.toString() ?: ""
            if (className.contains("ClipboardListener", ignoreCase = true) ||
                className.contains("ClipboardOverlay", ignoreCase = true) ||
                className.contains("Clipboard", ignoreCase = true)
            ) {
                Log.d(TAG, "SystemUI clipboard UI ($className) detected")
                onCopyDetected(currentForegroundApp)
            }
        }
    }
}
