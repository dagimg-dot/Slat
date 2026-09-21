package com.dagimg.slat.overlay

import com.dagimg.slat.data.ClipboardEntity

data class ClipboardUiState(
    val items: List<ClipboardEntity> = emptyList(),
    val revealedSensitiveIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
)
