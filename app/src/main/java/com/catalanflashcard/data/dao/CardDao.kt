package com.catalanflashcard.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.catalanflashcard.data.entity.Card
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Insert
    suspend fun insert(card: Card)

    @Insert
    suspend fun insertAll(cards: List<Card>)

    @Update
    suspend fun update(card: Card)

    @Upsert
    suspend fun upsert(card: Card)

    @Query("SELECT * FROM cards WHERE id = :id AND deletedAt IS NULL")
    suspend fun getCard(id: String): Card?

    @Query("SELECT * FROM cards WHERE deckId = :deckId AND deletedAt IS NULL")
    fun getAllCards(deckId: String): Flow<List<Card>>

    @Query("SELECT * FROM cards WHERE deckId = :deckId AND deletedAt IS NULL AND nextReviewTime <= :now ORDER BY nextReviewTime ASC")
    suspend fun getDueCards(deckId: String, now: Long = System.currentTimeMillis()): List<Card>

    @Query("SELECT COUNT(*) FROM cards WHERE deckId = :deckId AND deletedAt IS NULL")
    fun getCardCount(deckId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards WHERE deckId = :deckId AND deletedAt IS NULL AND nextReviewTime <= :now")
    fun getDueCardCount(deckId: String, now: Long): Flow<Int>

    // Delta for sync: rows touched since the last successful sync (tombstones
    // included). Uses >= so same-millisecond edits at the cursor boundary aren't
    // skipped; re-sending a row is safe because sync is idempotent.
    @Query("SELECT * FROM cards WHERE updatedAt >= :since")
    suspend fun getChangedSince(since: Long): List<Card>

    // Soft delete: mark as a tombstone instead of physically removing the row.
    @Query("UPDATE cards SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    // Cascade soft-delete when the parent deck is removed.
    @Query("UPDATE cards SET deletedAt = :now, updatedAt = :now WHERE deckId = :deckId AND deletedAt IS NULL")
    suspend fun softDeleteByDeck(deckId: String, now: Long = System.currentTimeMillis())
}
