package com.catalanflashcard.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

// updatedAt is indexed because the sync delta query filters on it.
@Entity(tableName = "decks", indices = [Index(value = ["updatedAt"])])
data class Deck(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Tombstone: null = active, non-null = soft-deleted. Kept locally so the
    // deletion propagates to the backend on the next sync.
    val deletedAt: Long? = null
)
