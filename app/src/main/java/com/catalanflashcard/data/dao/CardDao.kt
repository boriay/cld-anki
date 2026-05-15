package com.catalanflashcard.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.catalanflashcard.data.entity.Card
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Insert
    suspend fun insert(card: Card): Long

    @Update
    suspend fun update(card: Card)

    @Delete
    suspend fun delete(card: Card)

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getCard(id: Long): Card?

    @Query("SELECT * FROM cards WHERE deckId = :deckId")
    fun getAllCards(deckId: Long): Flow<List<Card>>

    @Query("SELECT * FROM cards WHERE deckId = :deckId AND nextReviewTime <= :now ORDER BY nextReviewTime ASC")
    suspend fun getDueCards(deckId: Long, now: Long = System.currentTimeMillis()): List<Card>

    @Query("SELECT COUNT(*) FROM cards WHERE deckId = :deckId")
    fun getCardCount(deckId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards WHERE deckId = :deckId AND nextReviewTime <= :now")
    suspend fun getDueCardCount(deckId: Long, now: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM cards WHERE deckId = :deckId")
    suspend fun deleteCardsByDeck(deckId: Long)
}
