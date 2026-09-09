package com.dagimg.glide

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.dagimg.glide.data.ClipboardRepository
import com.dagimg.glide.service.ClipboardService
import com.dagimg.glide.service.GlideAccessibilityService
import com.dagimg.glide.ui.components.ClearHistoryBottomSheet
import com.dagimg.glide.ui.screens.MainScreen
import com.dagimg.glide.ui.theme.GlideTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var isServiceEnabled by mutableStateOf(false)
    private var hasOverlayPermission by mutableStateOf(false)
    private var hasAccessibilityPermission by mutableStateOf(false)
    private var hasNotificationPermission by mutableStateOf(false)

    private val overlayPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            checkPermissions()
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) {
            checkPermissions()
        }

    private lateinit var repository: ClipboardRepository

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = appContainer.clipboardRepository

        val prefs = getSharedPreferences("glide_prefs", Context.MODE_PRIVATE)
        isServiceEnabled = prefs.getBoolean("service_enabled", false)

        setContent {
            GlideTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val scope = rememberCoroutineScope()
                    val snackbarHostState = remember { SnackbarHostState() }
                    var showClearBottomSheet by remember { mutableStateOf(false) }
                    val sheetState = rememberModalBottomSheetState()

                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                    ) { paddingValues ->
                        Box(modifier = Modifier.padding(paddingValues)) {
                            MainScreen(
                                isEnabled = isServiceEnabled,
                                hasOverlayPermission = hasOverlayPermission,
                                hasAccessibilityPermission = hasAccessibilityPermission,
                                hasNotificationPermission = hasNotificationPermission,
                                onToggle = { toggleService() },
                                onRequestOverlayPermission = { requestOverlayPermission() },
                                onRequestAccessibilityPermission = { openAccessibilitySettings() },
                                onRequestNotificationPermission = { requestNotificationPermission() },
                                onOpenClearDialog = { showClearBottomSheet = true },
                                onSeedHistory = {
                                    scope.launch {
                                        repository.seedTestData(300)
                                        snackbarHostState.showSnackbar("Seeded 300 test clipboard items")
                                    }
                                },
                            )

                            if (showClearBottomSheet) {
                                ClearHistoryBottomSheet(
                                    sheetState = sheetState,
                                    onDismissRequest = { showClearBottomSheet = false },
                                    onClearUnpinned = {
                                        scope.launch {
                                            repository.clearAllUnpinned()
                                            showClearBottomSheet = false
                                            snackbarHostState.showSnackbar("Unpinned history cleared")
                                        }
                                    },
                                    onClearEverything = {
                                        scope.launch {
                                            repository.clearAll()
                                            clearSystemClipboard()
                                            showClearBottomSheet = false
                                            snackbarHostState.showSnackbar("All clipboard history cleared")
                                        }
                                    },
                                    onCancel = { showClearBottomSheet = false },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        hasOverlayPermission = Settings.canDrawOverlays(this)
        hasAccessibilityPermission = GlideAccessibilityService.isRunning()
        hasNotificationPermission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
    }

    private fun toggleService() {
        if (!isServiceEnabled) {
            if (!hasOverlayPermission) {
                requestOverlayPermission()
                return
            }
            if (!hasAccessibilityPermission) {
                openAccessibilitySettings()
                return
            }

            ClipboardService.start(this)
            isServiceEnabled = true
        } else {
            ClipboardService.stop(this)
            isServiceEnabled = false
        }

        getSharedPreferences("glide_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("service_enabled", isServiceEnabled)
            .apply()
    }

    private fun requestOverlayPermission() {
        val intent =
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
        overlayPermissionLauncher.launch(intent)
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun clearSystemClipboard() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                cm.clearPrimaryClip()
            } else {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
            }
        } catch (_: Exception) {
        }
    }
}
