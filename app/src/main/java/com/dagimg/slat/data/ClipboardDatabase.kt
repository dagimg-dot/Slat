package com.dagimg.slat.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ClipboardEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ClipboardDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao

    companion object {
        @Volatile
        private var instance: ClipboardDatabase? = null

        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE clipboard_items ADD COLUMN isSensitive INTEGER NOT NULL DEFAULT 0",
                    )
                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS index_clipboard_items_isPinned_timestamp 
                        ON clipboard_items(isPinned, timestamp)
                        """.trimIndent(),
                    )
                }
            }

        fun getInstance(context: Context): ClipboardDatabase =
            instance ?: synchronized(this) {
                val newInstance =
                    Room.databaseBuilder(
                        context.applicationContext,
                        ClipboardDatabase::class.java,
                        "slat_clipboard.db",
                    )
                        .addMigrations(MIGRATION_1_2)
                        .fallbackToDestructiveMigration()
                        .build()
                instance = newInstance
                newInstance
            }
    }
}
