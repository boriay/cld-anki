package com.catalanflashcard.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.catalanflashcard.data.dao.CardDao
import com.catalanflashcard.data.dao.DeckDao
import com.catalanflashcard.data.entity.Card
import com.catalanflashcard.data.entity.Deck

@Database(entities = [Deck::class, Card::class], version = 4, exportSchema = true)
abstract class FlashcardDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao

    companion object {
        @Volatile
        var INSTANCE: FlashcardDatabase? = null
            private set

        // v2 -> v3: add the deletedAt tombstone column to both tables.
        // A real migration preserves the user's existing decks/cards.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE decks ADD COLUMN deletedAt INTEGER")
                db.execSQL("ALTER TABLE cards ADD COLUMN deletedAt INTEGER")
            }
        }

        // v3 -> v4: index updatedAt on both tables (the sync delta filters on it).
        // Index names must match Room's generated convention.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_decks_updatedAt ON decks (updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cards_updatedAt ON cards (updatedAt)")
            }
        }

        fun getDatabase(context: Context): FlashcardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FlashcardDatabase::class.java,
                    "flashcard_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    // Destructive fallback ONLY from v1 — the Long->UUID PK change
                    // has no data-preserving path. Any later missing migration
                    // (e.g. v4->v5) fails loudly instead of silently wiping data.
                    .fallbackToDestructiveMigrationFrom(false, 1)
                    .addCallback(InitialDataCallback(context.applicationContext))
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
