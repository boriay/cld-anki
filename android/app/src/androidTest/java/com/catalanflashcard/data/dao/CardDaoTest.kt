package com.catalanflashcard.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.catalanflashcard.data.database.FlashcardDatabase
import com.catalanflashcard.data.entity.Card
import com.catalanflashcard.data.entity.Deck
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardDaoTest {
    private lateinit var database: FlashcardDatabase
    private lateinit var cardDao: CardDao
    private lateinit var deckDao: DeckDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FlashcardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cardDao = database.cardDao()
        deckDao = database.deckDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertCard_canRetrieveCard() = runTest {
        val deck = Deck(name = "Test Deck")
        deckDao.insert(deck)

        val card = Card(deckId = deck.id, front = "Test", back = "Back")
        cardDao.insert(card)

        val retrieved = cardDao.getCard(card.id)
        org.junit.Assert.assertNotNull(retrieved)
        org.junit.Assert.assertEquals("Test", retrieved?.front)
    }

    @Test
    fun getAllCards_returnsFlowOfCards() = runTest {
        val deck = Deck(name = "Test Deck")
        deckDao.insert(deck)

        val card1 = Card(deckId = deck.id, front = "Front1", back = "Back1")
        val card2 = Card(deckId = deck.id, front = "Front2", back = "Back2")
        cardDao.insert(card1)
        cardDao.insert(card2)

        val cards = cardDao.getAllCards(deck.id).first()
        org.junit.Assert.assertEquals(2, cards.size)
    }

    @Test
    fun softDelete_hidesCardFromActiveQueries() = runTest {
        val deck = Deck(name = "Test Deck")
        deckDao.insert(deck)

        val card = Card(deckId = deck.id, front = "Test", back = "Back")
        cardDao.insert(card)

        cardDao.softDelete(card.id)

        // getCard filters out tombstones, so the card is no longer visible.
        val retrieved = cardDao.getCard(card.id)
        org.junit.Assert.assertNull(retrieved)

        // But the tombstone row survives so it can sync to the backend.
        val changed = cardDao.getChangedSince(0L)
        org.junit.Assert.assertEquals(1, changed.size)
        org.junit.Assert.assertNotNull(changed[0].deletedAt)
    }

    @Test
    fun getDueCards_returnsOnlyDueCards() = runTest {
        val deck = Deck(name = "Test Deck")
        deckDao.insert(deck)
        val now = System.currentTimeMillis()

        val dueCard = Card(deckId = deck.id, front = "Due", back = "Back", nextReviewTime = now - 1000)
        val futureCard = Card(deckId = deck.id, front = "Future", back = "Back", nextReviewTime = now + 1000)

        cardDao.insert(dueCard)
        cardDao.insert(futureCard)

        val dueCards = cardDao.getDueCards(deck.id, now)
        org.junit.Assert.assertEquals(1, dueCards.size)
        org.junit.Assert.assertEquals("Due", dueCards[0].front)
    }

    @Test
    fun update_updatesCard() = runTest {
        val deck = Deck(name = "Test Deck")
        deckDao.insert(deck)

        val card = Card(deckId = deck.id, front = "Test", back = "Back", interval = 1)
        cardDao.insert(card)

        cardDao.update(card.copy(interval = 3))

        val retrieved = cardDao.getCard(card.id)
        org.junit.Assert.assertEquals(3, retrieved?.interval)
    }
}
