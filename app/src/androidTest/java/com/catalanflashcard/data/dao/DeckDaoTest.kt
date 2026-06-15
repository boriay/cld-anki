package com.catalanflashcard.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.catalanflashcard.data.database.FlashcardDatabase
import com.catalanflashcard.data.entity.Deck
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeckDaoTest {
    private lateinit var database: FlashcardDatabase
    private lateinit var deckDao: DeckDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FlashcardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deckDao = database.deckDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertDeck_canRetrieveDeck() = runTest {
        val deck = Deck(name = "Test Deck", description = "Test Description")
        val deckId = deckDao.insert(deck)

        val retrieved = deckDao.getDeckFlow(deckId).firstOrNull()
        org.junit.Assert.assertNotNull(retrieved)
        org.junit.Assert.assertEquals("Test Deck", retrieved?.name)
    }

    @Test
    fun getAllDecks_returnsFlowOfDecks() = runTest {
        val deck1 = Deck(name = "Deck 1")
        val deck2 = Deck(name = "Deck 2")
        deckDao.insert(deck1)
        deckDao.insert(deck2)

        val decks = deckDao.getAllDecks().first()
        org.junit.Assert.assertEquals(2, decks.size)
    }

    @Test
    fun updateDeck_updatesName() = runTest {
        val deck = Deck(name = "Original Name")
        val deckId = deckDao.insert(deck)

        val updated = deck.copy(id = deckId, name = "Updated Name")
        deckDao.update(updated)

        val retrieved = deckDao.getDeckFlow(deckId).firstOrNull()
        org.junit.Assert.assertEquals("Updated Name", retrieved?.name)
    }

    @Test
    fun deleteDeck_removesFromDatabase() = runTest {
        val deck = Deck(name = "Test Deck")
        val deckId = deckDao.insert(deck)

        deckDao.deleteDeck(deckId)

        val retrieved = deckDao.getDeckFlow(deckId).firstOrNull()
        org.junit.Assert.assertNull(retrieved)
    }
}
