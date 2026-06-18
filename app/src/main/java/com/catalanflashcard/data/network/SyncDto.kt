package com.catalanflashcard.data.network

import com.google.gson.annotations.SerializedName

data class DeckDto(
    val id: String,
    val name: String,
    val language: String? = null,
    val pinned: Boolean = false,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class CardDto(
    val id: String,
    @SerializedName("deck_id") val deckId: String,
    val front: String,
    val back: String,
    val interval: Int,
    @SerializedName("ease_factor") val easeFactor: Double,
    val repetitions: Int,
    @SerializedName("next_review_time") val nextReviewTime: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("deleted_at") val deletedAt: String? = null
)

data class SyncRequest(
    @SerializedName("last_synced_at") val lastSyncedAt: String,
    val decks: List<DeckDto>,
    val cards: List<CardDto>
)

data class SyncResponse(
    @SerializedName("synced_at") val syncedAt: String,
    val decks: List<DeckDto>,
    val cards: List<CardDto>
)
