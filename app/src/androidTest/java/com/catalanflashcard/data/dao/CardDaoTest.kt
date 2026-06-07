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
        val deckId = deckDao.insert(deck)

        val card = Card(deckId = deckId, front = "Test", back = "Back")
        val cardId = cardDao.insert(card)

        val retrieved = cardDao.getCard(cardId)
        assert(retrieved != null)
        assert(retrieved?.front == "Test")
    }

    @Test
    fun getAllCards_returnsFlowOfCards() = runTest {
        val deck = Deck(name = "Test Deck")
        val deckId = deckDao.insert(deck)

        val card1 = Card(deckId = deckId, front = "Front1", back = "Back1")
        val card2 = Card(deckId = deckId, front = "Front2", back = "Back2")
        cardDao.insert(card1)
        cardDao.insert(card2)

        val cards = cardDao.getAllCards(deckId).first()
        assert(cards.size == 2)
    }

    @Test
    fun deleteCard_removesFromDatabase() = runTest {
        val deck = Deck(name = "Test Deck")
        val deckId = deckDao.insert(deck)

        val card = Card(deckId = deckId, front = "Test", back = "Back")
        val cardId = cardDao.insert(card)

        cardDao.delete(card.copy(id = cardId))

        val retrieved = cardDao.getCard(cardId)
        assert(retrieved == null)
    }

    @Test
    fun getDueCards_returnsOnlyDueCards() = runTest {
        val deck = Deck(name = "Test Deck")
        val deckId = deckDao.insert(deck)
        val now = System.currentTimeMillis()

        val dueCard = Card(deckId = deckId, front = "Due", back = "Back", nextReviewTime = now - 1000)
        val futureCard = Card(deckId = deckId, front = "Future", back = "Back", nextReviewTime = now + 1000)

        cardDao.insert(dueCard)
        cardDao.insert(futureCard)

        val dueCards = cardDao.getDueCards(deckId, now)
        assert(dueCards.size == 1)
        assert(dueCards[0].front == "Due")
    }

    @Test
    fun updateCardReview_updatesCardInTransaction() = runTest {
        val deck = Deck(name = "Test Deck")
        val deckId = deckDao.insert(deck)

        val card = Card(deckId = deckId, front = "Test", back = "Back", interval = 1)
        val cardId = cardDao.insert(card)

        val updatedCard = card.copy(id = cardId, interval = 3)
        cardDao.updateCardReview(updatedCard)

        val retrieved = cardDao.getCard(cardId)
        assert(retrieved?.interval == 3)
    }
}
