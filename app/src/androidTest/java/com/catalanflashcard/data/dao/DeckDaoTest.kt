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
        val deck = Deck(name = "Test Deck")
        deckDao.insert(deck)

        val retrieved = deckDao.getDeckFlow(deck.id).firstOrNull()
        org.junit.Assert.assertNotNull(retrieved)
        org.junit.Assert.assertEquals("Test Deck", retrieved?.name)
    }

    @Test
    fun getDecks_filtersByLanguage_butKeepsUserDecks() = runTest {
        deckDao.insert(Deck(name = "EN", language = "en"))
        deckDao.insert(Deck(name = "ES", language = "es"))
        deckDao.insert(Deck(name = "User", language = null)) // user-created, always visible

        val es = deckDao.getDecks("es").first()
        org.junit.Assert.assertEquals(setOf("ES", "User"), es.map { it.name }.toSet())
    }

    @Test
    fun getDecks_includesPinnedDeckOfAnotherLanguage() = runTest {
        val esDeck = Deck(name = "ES", language = "es")
        deckDao.insert(esDeck)
        deckDao.insert(Deck(name = "EN", language = "en"))

        // Using the ES deck pins it; it must stay visible under the EN filter.
        deckDao.pin(esDeck.id)

        val en = deckDao.getDecks("en").first()
        org.junit.Assert.assertEquals(setOf("ES", "EN"), en.map { it.name }.toSet())
    }

    @Test
    fun pin_isIdempotent_bumpsUpdatedAtOnlyOnce() = runTest {
        val deck = Deck(name = "ES", language = "es", updatedAt = 100)
        deckDao.insert(deck)

        deckDao.pin(deck.id, now = 200)
        val afterFirst = deckDao.getDeckFlow(deck.id).first()!!
        org.junit.Assert.assertTrue(afterFirst.pinned)
        org.junit.Assert.assertEquals(200L, afterFirst.updatedAt)

        // Already pinned -> a second pin is a no-op (updatedAt unchanged).
        deckDao.pin(deck.id, now = 300)
        val afterSecond = deckDao.getDeckFlow(deck.id).first()!!
        org.junit.Assert.assertEquals(200L, afterSecond.updatedAt)
    }

    @Test
    fun updateDeck_updatesName() = runTest {
        val deck = Deck(name = "Original Name")
        deckDao.insert(deck)

        deckDao.update(deck.copy(name = "Updated Name"))

        val retrieved = deckDao.getDeckFlow(deck.id).firstOrNull()
        org.junit.Assert.assertEquals("Updated Name", retrieved?.name)
    }

    @Test
    fun softDelete_hidesDeckFromActiveQueries() = runTest {
        val deck = Deck(name = "Test Deck")
        deckDao.insert(deck)

        deckDao.softDelete(deck.id)

        // getDeckFlow filters out tombstones, so the deck is no longer visible.
        val retrieved = deckDao.getDeckFlow(deck.id).firstOrNull()
        org.junit.Assert.assertNull(retrieved)

        // But the tombstone row survives so it can sync to the backend.
        val changed = deckDao.getChangedSince(0L)
        org.junit.Assert.assertEquals(1, changed.size)
        org.junit.Assert.assertNotNull(changed[0].deletedAt)
    }
}
