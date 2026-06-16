package com.catalanflashcard.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.catalanflashcard.data.entity.Deck
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {
    @Insert
    suspend fun insert(deck: Deck)

    @Update
    suspend fun update(deck: Deck)

    @Upsert
    suspend fun upsert(deck: Deck)

    @Query("SELECT * FROM decks WHERE id = :id AND deletedAt IS NULL")
    fun getDeckFlow(id: String): Flow<Deck?>

    @Query("SELECT * FROM decks WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun getAllDecks(): Flow<List<Deck>>

    // Delta for sync: rows touched since the last successful sync (tombstones
    // included). Uses >= so same-millisecond edits at the cursor boundary aren't
    // skipped; re-sending a row is safe because sync is idempotent.
    @Query("SELECT * FROM decks WHERE updatedAt >= :since")
    suspend fun getChangedSince(since: Long): List<Deck>

    // Soft delete: mark as a tombstone instead of physically removing the row.
    @Query("UPDATE decks SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())
}
