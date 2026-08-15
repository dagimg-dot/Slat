package com.dagimg.glide.overlay

import com.dagimg.glide.data.ClipboardEntity

data class ClipboardUiState(
    val items: List<ClipboardEntity> = emptyList(),
    val revealedSensitiveIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
)
