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
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlashcardRepositoryTest {
    @Mock private lateinit var deckDao: DeckDao
    @Mock private lateinit var cardDao: CardDao
    private lateinit var repository: FlashcardRepository

    companion object {
        const val DECK_ID = "deck-uuid-1"
        const val CARD_ID = "card-uuid-1"
    }

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = FlashcardRepository(deckDao, cardDao)
    }

    @Test
    fun getAllDecks_delegatesToDeckDao() = runTest {
        val decks = listOf(Deck(id = DECK_ID, name = "Test"))
        whenever(deckDao.getAllDecks()).thenReturn(flowOf(decks))

        val result = repository.getAllDecks()
        result.collect { }

        verify(deckDao).getAllDecks()
    }

    @Test
    fun createDeck_callsDeckDaoInsert() = runTest {
        repository.createDeck("Test Deck", "Description")

        verify(deckDao).insert(org.mockito.kotlin.any())
    }

    @Test
    fun deleteDeck_softDeletesDeckAndCascadesToCards() = runTest {
        repository.deleteDeck(DECK_ID)

        verify(cardDao).softDeleteByDeck(eq(DECK_ID), org.mockito.kotlin.any())
        verify(deckDao).softDelete(eq(DECK_ID), org.mockito.kotlin.any())
    }

    @Test
    fun updateCardReview_updatesCardWithSpacedRepetition() = runTest {
        val card = Card(id = CARD_ID, deckId = DECK_ID, front = "Front", back = "Back")
        whenever(cardDao.getCard(CARD_ID)).thenReturn(card)

        repository.updateCardReview(CARD_ID, 4)

        verify(cardDao).getCard(CARD_ID)
        verify(cardDao).update(org.mockito.kotlin.any())
    }
}
