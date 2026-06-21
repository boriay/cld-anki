package com.catalanflashcard.data.database

import android.content.ContentValues
import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/**
 * Seeds the default decks/cards when the Room database is first created. Runs at
 * DB-creation time (no auth, no network) so the app works offline on first
 * launch. The same data, used after a sign-out wipe, is applied via [LocalSeeder].
 */
class InitialDataCallback(private val context: Context) : RoomDatabase.Callback() {

    private companion object {
        const val TABLE_DECKS = "decks"
        const val TABLE_CARDS = "cards"

        const val COL_ID = "id"
        const val COL_NAME = "name"
        const val COL_LANGUAGE = "language"
        const val COL_PINNED = "pinned"
        const val COL_DECK_ID = "deckId"
        const val COL_FRONT = "front"
        const val COL_BACK = "back"
        const val COL_INTERVAL = "interval"
        const val COL_EASE_FACTOR = "easeFactor"
        const val COL_REPETITIONS = "repetitions"
        const val COL_NEXT_REVIEW_TIME = "nextReviewTime"
        const val COL_CREATED_AT = "createdAt"
        const val COL_UPDATED_AT = "updatedAt"
    }

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seed(db)
    }

    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
        super.onDestructiveMigration(db)
        seed(db)
    }

    private fun seed(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            // Seed one deck per UI language. All three are created up front (pinned
            // = 0); the deck list shows only the current language, and a deck pins
            // itself once used — so switching language lets the user collect all of
            // them. Catalan fronts are shared; only the back (translation) differs.
            for (deck in DefaultSeedData.decks) {
                val deckId = UUID.randomUUID().toString()
                val deckValues = ContentValues().apply {
                    put(COL_ID, deckId)
                    put(COL_NAME, deck.name)
                    put(COL_LANGUAGE, deck.language)
                    put(COL_PINNED, 0)
                    put(COL_CREATED_AT, now)
                    put(COL_UPDATED_AT, now)
                }
                if (db.insert(TABLE_DECKS, 0, deckValues) == -1L) {
                    throw IllegalStateException("Failed to insert initial deck ${deck.language}")
                }

                for (card in DefaultSeedData.cards) {
                    val cardValues = ContentValues().apply {
                        put(COL_ID, UUID.randomUUID().toString())
                        put(COL_DECK_ID, deckId)
                        put(COL_FRONT, card.front)
                        put(COL_BACK, card.backFor(deck.language))
                        put(COL_INTERVAL, 1)
                        put(COL_EASE_FACTOR, 2.5f)
                        put(COL_REPETITIONS, 0)
                        put(COL_NEXT_REVIEW_TIME, now)
                        put(COL_CREATED_AT, now)
                        put(COL_UPDATED_AT, now)
                    }
                    if (db.insert(TABLE_CARDS, 0, cardValues) == -1L) {
                        throw IllegalStateException("Failed to insert initial card: ${card.front}")
                    }
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
