package com.catalanflashcard.data.entity

import androidx.room.ColumnInfo
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
    // BCP-47 language tag ("en"/"es"/"ru") of the deck's translation side. Seeded
    // decks are tagged so the list shows only the current UI language; null marks
    // a user-created deck, which is always visible regardless of language.
    val language: String? = null,
    // Once a deck is used (first card answered) it is pinned and stays visible
    // even after switching language — lets the user accumulate all three decks.
    // defaultValue matches the v5->v6 ADD COLUMN ... DEFAULT 0 so Room's schema
    // validation passes on migrated databases.
    @ColumnInfo(defaultValue = "0")
    val pinned: Boolean = false,
    // Tombstone: null = active, non-null = soft-deleted. Kept locally so the
    // deletion propagates to the backend on the next sync.
    val deletedAt: Long? = null
)
