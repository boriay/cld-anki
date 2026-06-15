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

@Database(entities = [Deck::class, Card::class], version = 3, exportSchema = true)
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

        fun getDatabase(context: Context): FlashcardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FlashcardDatabase::class.java,
                    "flashcard_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .addCallback(InitialDataCallback(context.applicationContext))
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
