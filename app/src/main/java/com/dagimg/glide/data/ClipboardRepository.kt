package com.dagimg.glide.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

interface ClipboardRepository {
    fun getAllItems(): Flow<List<ClipboardEntity>>

    suspend fun addText(
        text: String,
        sourceApp: String? = null,
        isSensitive: Boolean = false,
    ): Boolean

    suspend fun addImage(
        bitmap: Bitmap,
        uri: String? = null,
        sourceApp: String? = null,
        isSensitive: Boolean = false,
    ): Boolean

    suspend fun addImageFromStream(
        uri: String? = null,
        sourceApp: String? = null,
        isSensitive: Boolean = false,
        openStream: () -> InputStream?,
    ): Boolean

    suspend fun togglePin(id: String)

    suspend fun delete(item: ClipboardEntity)

    suspend fun clearAllUnpinned()

    fun shouldIgnore(
        text: String?,
        uri: String?,
    ): Boolean
}

class ClipboardRepositoryImpl(
    private val dao: ClipboardDao,
    filesDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ClipboardRepository {
    companion object {
        private const val TAG = "ClipboardRepository"
        private const val MAX_ITEMS = 50
        private const val IMAGES_DIR = "clipboard_images"
        private const val COOLDOWN_MS = 500L
        private const val MAX_IMAGE_WIDTH = 1080
        private const val MAX_IMAGE_HEIGHT = 1920
    }

    private val imagesDir: File = File(filesDir, IMAGES_DIR).apply { mkdirs() }
    private val captureMutex = Mutex()

    @Volatile private var lastTextHash: Int = 0

    @Volatile private var lastUri: String? = null

    @Volatile private var lastTimestamp: Long = 0

    override fun getAllItems(): Flow<List<ClipboardEntity>> = dao.getAllOrdered()

    override fun shouldIgnore(
        text: String?,
        uri: String?,
    ): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastTimestamp < COOLDOWN_MS) {
            if (uri != null && uri == lastUri) return true
            if (text != null && text.hashCode() == lastTextHash) return true
        }

        if (uri?.contains(".fileprovider") == true) return true
        if (text?.contains(".fileprovider") == true) return true
        if (text?.startsWith("[Image:") == true) return true

        return false
    }

    private fun updateLastCapture(
        text: String?,
        uri: String?,
    ) {
        lastTimestamp = System.currentTimeMillis()
        lastUri = uri
        lastTextHash = text?.hashCode() ?: 0
    }

    override suspend fun addText(
        text: String,
        sourceApp: String?,
        isSensitive: Boolean,
    ): Boolean =
        withContext(ioDispatcher) {
            if (text.isBlank()) return@withContext false
            if (shouldIgnore(text, null)) return@withContext false

            captureMutex.withLock {
                if (shouldIgnore(text, null)) return@withLock false
                updateLastCapture(text, null)

                val existing = dao.findByText(text)
                if (existing != null) {
                    dao.updateTimestamp(existing.id)
                    Log.d(TAG, "Duplicate text found, updated timestamp")
                    return@withLock false
                }

                enforceMaxItems()

                val item =
                    ClipboardEntity(
                        text = text,
                        sourceApp = sourceApp,
                        isSensitive = isSensitive,
                    )
                dao.insert(item)
                Log.d(TAG, "Stored text clip: ${text.take(40)}...")
                return@withLock true
            }
        }

    override suspend fun addImage(
        bitmap: Bitmap,
        uri: String?,
        sourceApp: String?,
        isSensitive: Boolean,
    ): Boolean =
        withContext(ioDispatcher) {
            if (shouldIgnore(null, uri)) return@withContext false

            captureMutex.withLock {
                if (shouldIgnore(null, uri)) return@withLock false
                updateLastCapture(null, uri)

                try {
                    enforceMaxItems()

                    val fileName = "clip_${UUID.randomUUID()}.png"
                    val imageFile = File(imagesDir, fileName)

                    FileOutputStream(imageFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }

                    val item =
                        ClipboardEntity(
                            imagePath = imageFile.absolutePath,
                            sourceApp = sourceApp,
                            isSensitive = isSensitive,
                        )
                    dao.insert(item)
                    Log.d(TAG, "Stored image clip: $fileName")
                    return@withLock true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save image", e)
                    return@withLock false
                }
            }
        }

    override suspend fun addImageFromStream(
        uri: String?,
        sourceApp: String?,
        isSensitive: Boolean,
        openStream: () -> InputStream?,
    ): Boolean =
        withContext(ioDispatcher) {
            if (shouldIgnore(null, uri)) return@withContext false

            captureMutex.withLock {
                if (shouldIgnore(null, uri)) return@withLock false

                val bitmap =
                    decodeSampledBitmap(openStream, MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT)
                        ?: return@withLock false

                updateLastCapture(null, uri)

                try {
                    enforceMaxItems()

                    val fileName = "clip_${UUID.randomUUID()}.png"
                    val imageFile = File(imagesDir, fileName)

                    FileOutputStream(imageFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }

                    val item =
                        ClipboardEntity(
                            imagePath = imageFile.absolutePath,
                            sourceApp = sourceApp,
                            isSensitive = isSensitive,
                        )
                    dao.insert(item)
                    Log.d(TAG, "Stored sampled image clip: $fileName")
                    return@withLock true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save sampled image", e)
                    return@withLock false
                } finally {
                    bitmap.recycle()
                }
            }
        }

    private fun decodeSampledBitmap(
        openStream: () -> InputStream?,
        reqWidth: Int,
        reqHeight: Int,
    ): Bitmap? {
        val options =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

        openStream()?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false

        return openStream()?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    override suspend fun togglePin(id: String) =
        withContext(ioDispatcher) {
            dao.togglePin(id)
        }

    override suspend fun delete(item: ClipboardEntity) =
        withContext(ioDispatcher) {
            item.imagePath?.let { path ->
                try {
                    File(path).delete()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete image file", e)
                }
            }
            dao.delete(item)
        }

    override suspend fun clearAllUnpinned() =
        withContext(ioDispatcher) {
            val items = dao.getOldestUnpinned(Int.MAX_VALUE)
            items.forEach { item ->
                item.imagePath?.let { path ->
                    try {
                        File(path).delete()
                    } catch (_: Exception) {
                    }
                }
            }
            dao.deleteAllUnpinned()
        }

    private suspend fun enforceMaxItems() {
        val count = dao.getCount()
        if (count >= MAX_ITEMS) {
            val toDelete = count - MAX_ITEMS + 1
            val oldestItems = dao.getOldestUnpinned(toDelete)
            oldestItems.forEach { item ->
                item.imagePath?.let { path ->
                    try {
                        File(path).delete()
                    } catch (_: Exception) {
                    }
                }
                dao.delete(item)
            }
            Log.d(TAG, "Evicted $toDelete old items")
        }
    }
}
