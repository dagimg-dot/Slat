package com.dagimg.glide.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ClipboardSeeder {
    suspend fun seedSampleData(
        dao: ClipboardDao,
        filesDir: File,
        count: Int = 300,
    ) = withContext(Dispatchers.IO) {
        val sampleApps =
            listOf(
                "com.android.chrome",
                "org.telegram.messenger",
                "com.whatsapp",
                "com.twitter.android",
                "com.github.android",
                "com.slack",
                "com.google.android.apps.docs",
                "com.spotify.music",
            )

        val sampleTexts =
            listOf(
                "https://github.com/JetBrains/kotlin/releases/tag/v2.0.21",
                "fun main() = runBlocking { launch { println(\"Hello Compose 120Hz!\") } }",
                "sudo apt-get update && sudo apt-get install -y build-essential libssl-dev",
                "Flight #ET801 departs at 10:45 AM from Terminal 2, Gate B14.",
                "0x71C81842F37da19b08f4430e3567Ea38148b598e",
                "Meeting Notes:\n- Finalize Q3 architecture roadmap\n- Fix 120Hz scroll recycling\n- Verify background capture",
                "The quick brown fox jumps over the lazy dog.",
                "docker run -d -p 8080:8080 --name glide-server glide/app:latest",
                "ssh -i ~/.ssh/id_ed25519 user@192.168.1.150 -p 2222",
                "SuperSecretPassword!2026",
                "curl -X POST https://api.glide.app/v1/sync -H 'Authorization: Bearer tok_sample_xyz'",
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor.",
                "2FA Verification code: 849-201 (Valid for 10 minutes)",
                "RGB(108, 92, 231) -> #6C5CE7",
                "SELECT * FROM clipboard_items WHERE isPinned = 1 ORDER BY timestamp DESC;",
            )

        val imagesDir = File(filesDir, "clipboard_images").apply { mkdirs() }
        val now = System.currentTimeMillis()

        for (i in 0 until count) {
            val isPinned = (i % 6 == 0)
            val isSensitive = (i % 8 == 0)
            val isImage = (i % 5 == 0)
            val timestamp = now - (i * 1000L * 60 * 20)
            val sourceApp = sampleApps[i % sampleApps.size]

            if (isImage) {
                val width = 400
                val height = 300
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val paint =
                    Paint().apply {
                        color =
                            when (i % 4) {
                                0 -> Color.parseColor("#6C5CE7")
                                1 -> Color.parseColor("#4ADE80")
                                2 -> Color.parseColor("#FBBF24")
                                else -> Color.parseColor("#EF4444")
                            }
                        isAntiAlias = true
                    }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

                paint.color = Color.WHITE
                paint.textSize = 28f
                canvas.drawText("Clip Image #$i", 40f, 160f, paint)

                val imageFile = File(imagesDir, "clip_seed_${UUID.randomUUID()}.png")
                FileOutputStream(imageFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
                bitmap.recycle()

                dao.insert(
                    ClipboardEntity(
                        id = UUID.randomUUID().toString(),
                        imagePath = imageFile.absolutePath,
                        timestamp = timestamp,
                        isPinned = isPinned,
                        isSensitive = isSensitive,
                        sourceApp = sourceApp,
                    ),
                )
            } else {
                val text = sampleTexts[i % sampleTexts.size] + if (i >= sampleTexts.size) " [#$i]" else ""
                dao.insert(
                    ClipboardEntity(
                        id = UUID.randomUUID().toString(),
                        text = text,
                        timestamp = timestamp,
                        isPinned = isPinned,
                        isSensitive = isSensitive,
                        sourceApp = sourceApp,
                    ),
                )
            }
        }
    }
}
