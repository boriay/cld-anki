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

    // Decks visible for the current UI language: those tagged with :lang, plus
    // user-created decks (language IS NULL) and any deck pinned by use — pinned
    // and user decks stay visible across language switches.
    @Query(
        "SELECT * FROM decks WHERE deletedAt IS NULL " +
            "AND (pinned = 1 OR language IS NULL OR language = :lang) " +
            "ORDER BY createdAt DESC"
    )
    fun getDecks(lang: String): Flow<List<Deck>>

    // Delta for sync: rows touched since the last successful sync (tombstones
    // included). Uses >= so same-millisecond edits at the cursor boundary aren't
    // skipped; re-sending a row is safe because sync is idempotent.
    @Query("SELECT * FROM decks WHERE updatedAt >= :since")
    suspend fun getChangedSince(since: Long): List<Deck>

    // Soft delete: mark as a tombstone instead of physically removing the row.
    // Idempotent — only stamps an active row, so retries don't keep bumping the
    // timestamp or re-emitting the same tombstone in the sync delta.
    @Query("UPDATE decks SET deletedAt = :now, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    // Pin a deck on first use so it survives language switches. Idempotent —
    // only an unpinned row is touched, so repeat answers don't keep bumping
    // updatedAt or re-emitting the row in the sync delta.
    @Query("UPDATE decks SET pinned = 1, updatedAt = :now WHERE id = :id AND pinned = 0")
    suspend fun pin(id: String, now: Long = System.currentTimeMillis())
}
