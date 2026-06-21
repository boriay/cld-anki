package com.catalanflashcard.data.database

import androidx.room.withTransaction
import com.catalanflashcard.data.entity.Card
import com.catalanflashcard.data.entity.Deck

/**
 * Runtime seeding/wiping of the local store, used when switching accounts. Both
 * operations run inside a single Room transaction so a crash can't leave a
 * half-wiped or half-seeded database (the FK between cards and decks would
 * otherwise be momentarily inconsistent). Seed IDs are random UUIDs — the same
 * as the offline first-launch seed ([InitialDataCallback]); they converge with
 * the server on the next sync (last-write-wins by id).
 */
class LocalSeeder(private val db: FlashcardDatabase) {

    /** True when the store holds no decks at all (including tombstoned). */
    suspend fun isEmpty(): Boolean = db.deckDao().countAll() == 0

    /** Atomically delete every local deck and card (cards first, FK child). */
    suspend fun wipe() = db.withTransaction {
        db.cardDao().deleteAll()
        db.deckDao().deleteAll()
    }

    /** Atomically insert the default decks/cards (one deck per UI language). */
    suspend fun seedDefaults() = db.withTransaction {
        val now = System.currentTimeMillis()
        for (seedDeck in DefaultSeedData.decks) {
            val deck = Deck(
                name = seedDeck.name,
                language = seedDeck.language,
                createdAt = now,
                updatedAt = now,
            )
            db.deckDao().insert(deck)
            val cards = DefaultSeedData.cards.map { c ->
                Card(
                    deckId = deck.id,
                    front = c.front,
                    back = c.backFor(seedDeck.language),
                    nextReviewTime = now,
                    createdAt = now,
                    updatedAt = now,
                )
            }
            db.cardDao().insertAll(cards)
        }
    }
}
