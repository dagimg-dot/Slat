package com.dagimg.glide.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dagimg.glide.ui.components.PermissionCard
import com.dagimg.glide.ui.components.ServiceStatusCard

@Composable
fun MainScreen(
    isEnabled: Boolean,
    hasOverlayPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    hasNotificationPermission: Boolean,
    onToggle: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenClearDialog: () -> Unit,
    onSeedHistory: () -> Unit,
) {
    val allPermissionsGranted = hasOverlayPermission && hasAccessibilityPermission

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color(0xFF0D0D0D),
                                    Color(0xFF1A1A1A),
                                ),
                        ),
                ).padding(24.dp)
                .statusBarsPadding(),
    ) {
        Text(
            text = "Glide",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )

        Text(
            text = "Clipboard Edge Panel",
            fontSize = 16.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(modifier = Modifier.height(48.dp))

        ServiceStatusCard(
            isEnabled = isEnabled,
            onToggle = onToggle,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Required Permissions",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        PermissionCard(
            title = "Display Over Apps",
            description = "Required to show the edge panel",
            isGranted = hasOverlayPermission,
            onClick = onRequestOverlayPermission,
        )

        Spacer(modifier = Modifier.height(8.dp))

        PermissionCard(
            title = "Accessibility Service",
            description = "Required for clipboard monitoring on Android 10+",
            isGranted = hasAccessibilityPermission,
            onClick = onRequestAccessibilityPermission,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Spacer(modifier = Modifier.height(8.dp))

            PermissionCard(
                title = "Notifications",
                description = "Show service running notification",
                isGranted = hasNotificationPermission,
                onClick = onRequestNotificationPermission,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        TextButton(
            onClick = onSeedHistory,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors =
                ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF6C5CE7),
                ),
        ) {
            Icon(Icons.Default.DataObject, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Seed 300 Test Items", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onOpenClearDialog,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors =
                ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFFEF4444),
                ),
        ) {
            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Clear Clipboard History", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!allPermissionsGranted) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = Color(0xFF3D2E1E),
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Grant all permissions to enable Glide",
                        color = Color(0xFFFBBF24),
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
