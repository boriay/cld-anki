package com.catalanflashcard.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.catalanflashcard.data.dao.CardDao
import com.catalanflashcard.data.dao.DeckDao
import com.catalanflashcard.data.entity.Card
import com.catalanflashcard.data.entity.Deck

@Database(entities = [Deck::class, Card::class], version = 1, exportSchema = true)
abstract class FlashcardDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao

    companion object {
        @Volatile
        var INSTANCE: FlashcardDatabase? = null
            private set

        fun getDatabase(context: Context): FlashcardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FlashcardDatabase::class.java,
                    "flashcard_database"
                )
                    .addCallback(InitialDataCallback())
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
