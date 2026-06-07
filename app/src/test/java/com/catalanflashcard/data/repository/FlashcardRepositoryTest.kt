package com.catalanflashcard.data.repository

import com.catalanflashcard.data.dao.CardDao
import com.catalanflashcard.data.dao.DeckDao
import com.catalanflashcard.data.entity.Card
import com.catalanflashcard.data.entity.Deck
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlashcardRepositoryTest {
    @Mock private lateinit var deckDao: DeckDao
    @Mock private lateinit var cardDao: CardDao
    private lateinit var repository: FlashcardRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = FlashcardRepository(deckDao, cardDao)
    }

    @Test
    fun getAllDecks_delegatesToDeckDao() = runTest {
        val decks = listOf(Deck(id = 1, name = "Test"))
        whenever(deckDao.getAllDecks()).thenReturn(flowOf(decks))

        val result = repository.getAllDecks()
        result.collect { }

        verify(deckDao).getAllDecks()
    }

    @Test
    fun createDeck_callsDeckDaoInsert() = runTest {
        whenever(deckDao.insert(org.mockito.kotlin.any())).thenReturn(1)

        repository.createDeck("Test Deck", "Description")

        verify(deckDao).insert(org.mockito.kotlin.any())
    }

    @Test
    fun deleteDeck_callsDeckDaoDelete() = runTest {
        whenever(deckDao.deleteDeck(1)).thenReturn(Unit)

        repository.deleteDeck(1)

        verify(deckDao).deleteDeck(1)
    }

    @Test
    fun updateCardReview_updatesCardWithSpacedRepetition() = runTest {
        val card = Card(id = 1, deckId = 1, front = "Front", back = "Back")
        whenever(cardDao.getCard(1)).thenReturn(card)
        whenever(cardDao.updateCardReview(org.mockito.kotlin.any())).thenReturn(Unit)

        repository.updateCardReview(1, 4)

        verify(cardDao).getCard(1)
        verify(cardDao).updateCardReview(org.mockito.kotlin.any())
    }
}
