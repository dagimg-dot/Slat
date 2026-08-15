package com.dagimg.glide.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "clipboard_items",
    indices = [
        Index(value = ["isPinned", "timestamp"]),
    ],
)
data class ClipboardEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val text: String? = null,
    val imagePath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isSensitive: Boolean = false,
    val sourceApp: String? = null,
) {
    val isText: Boolean get() = text != null && imagePath == null
    val isImage: Boolean get() = imagePath != null

    fun getPreview(maxLength: Int = 100): String =
        when {
            isSensitive -> "•••••••• (Sensitive)"
            isImage -> "[Image]"
            text != null -> if (text.length > maxLength) text.take(maxLength) + "…" else text
            else -> "[Empty]"
        }
}
