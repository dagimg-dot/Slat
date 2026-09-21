package com.dagimg.slat.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {
    @Query(
        """
        SELECT * FROM clipboard_items 
        ORDER BY isPinned DESC, timestamp DESC
    """,
    )
    fun getAllOrdered(): Flow<List<ClipboardEntity>>

    @Query("SELECT * FROM clipboard_items WHERE id = :id")
    suspend fun getById(id: String): ClipboardEntity?

    @Query("SELECT * FROM clipboard_items WHERE text = :text LIMIT 1")
    suspend fun findByText(text: String): ClipboardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ClipboardEntity)

    @Update
    suspend fun update(item: ClipboardEntity)

    @Delete
    suspend fun delete(item: ClipboardEntity)

    @Query("DELETE FROM clipboard_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM clipboard_items WHERE isPinned = 0")
    suspend fun deleteAllUnpinned()

    @Query("DELETE FROM clipboard_items")
    suspend fun deleteAll()

    @Query("UPDATE clipboard_items SET isPinned = NOT isPinned WHERE id = :id")
    suspend fun togglePin(id: String)

    @Query("SELECT COUNT(*) FROM clipboard_items")
    suspend fun getCount(): Int

    @Query(
        """
        SELECT * FROM clipboard_items 
        WHERE isPinned = 0 
        ORDER BY timestamp ASC 
        LIMIT :count
    """,
    )
    suspend fun getOldestUnpinned(count: Int): List<ClipboardEntity>

    @Query("UPDATE clipboard_items SET timestamp = :timestamp WHERE id = :id")
    suspend fun updateTimestamp(
        id: String,
        timestamp: Long = System.currentTimeMillis(),
    )
}
