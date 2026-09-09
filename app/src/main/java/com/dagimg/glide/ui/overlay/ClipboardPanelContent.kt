package com.dagimg.glide.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dagimg.glide.data.ClipboardEntity
import com.dagimg.glide.data.ClipboardRepository
import com.dagimg.glide.overlay.ClipboardUiState
import com.dagimg.glide.ui.theme.SurfaceContainerLowDark
import com.dagimg.glide.ui.theme.TextPrimary
import kotlinx.coroutines.flow.map

@Composable
fun ClipboardPanelContent(
    repository: ClipboardRepository,
    onItemClick: (ClipboardEntity) -> Unit,
    onItemPin: (ClipboardEntity) -> Unit,
    onItemDelete: (ClipboardEntity) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemsFlow =
        remember(repository) {
            repository.getAllItems().map { ClipboardUiState(items = it) }
        }
    val uiState by itemsFlow.collectAsState(initial = ClipboardUiState(isLoading = true))
    var revealedIds by remember { mutableStateOf(setOf<String>()) }
    var activeActionCardId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            activeActionCardId = null
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(SurfaceContainerLowDark)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    activeActionCardId = null
                }.padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Clipboard",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )

            IconButton(
                onClick = {
                    activeActionCardId = null
                    onSettingsClick()
                },
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.items.isEmpty() && !uiState.isLoading) {
            ClipboardEmptyState()
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = uiState.items,
                    key = { it.id },
                    contentType = { if (it.isImage) 1 else 0 },
                ) { item ->
                    val isRevealed = revealedIds.contains(item.id)
                    val showActions = activeActionCardId == item.id

                    ClipboardItemCard(
                        item = item,
                        isRevealed = isRevealed,
                        showActions = showActions,
                        onToggleReveal = {
                            activeActionCardId = null
                            revealedIds =
                                if (isRevealed) {
                                    revealedIds - item.id
                                } else {
                                    revealedIds + item.id
                                }
                        },
                        onClick = {
                            if (activeActionCardId != null) {
                                activeActionCardId = null
                            } else {
                                onItemClick(item)
                            }
                        },
                        onLongClick = {
                            activeActionCardId = item.id
                        },
                        onDismissActions = {
                            if (activeActionCardId == item.id) {
                                activeActionCardId = null
                            }
                        },
                        onPin = { onItemPin(item) },
                        onDelete = { onItemDelete(item) },
                    )
                }
            }
        }
    }
}
