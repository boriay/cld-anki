package com.catalanflashcard.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = Deck::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["deckId"]),
        Index(value = ["deckId", "nextReviewTime"]),
        // Sync delta query filters on updatedAt.
        Index(value = ["updatedAt"])
    ]
)
data class Card(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val deckId: String,
    val front: String,
    val back: String,
    val interval: Int = 1,
    val easeFactor: Float = 2.5f,
    val repetitions: Int = 0,
    val nextReviewTime: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Tombstone: null = active, non-null = soft-deleted (see Deck.deletedAt).
    val deletedAt: Long? = null
)
