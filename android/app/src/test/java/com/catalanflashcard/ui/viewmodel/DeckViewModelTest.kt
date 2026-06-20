package com.catalanflashcard.ui.viewmodel

import com.catalanflashcard.data.entity.Card
import com.catalanflashcard.data.entity.Deck
import com.catalanflashcard.data.repository.FlashcardRepository
import com.catalanflashcard.data.repository.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class DeckViewModelTest {
    @Mock private lateinit var repository: FlashcardRepository
    @Mock private lateinit var syncRepository: SyncRepository
    private lateinit var viewModel: DeckViewModel
    private val testDispatcher = StandardTestDispatcher()

    companion object {
        const val DECK_ID = "deck-uuid-1"
    }

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_loadsDecks() = runTest {
        val decks = listOf(Deck(id = DECK_ID, name = "Test"))
        whenever(repository.getDecks(any())).thenReturn(flowOf(decks))

        viewModel = DeckViewModel(repository, syncRepository)
        advanceUntilIdle()

        org.junit.Assert.assertTrue(viewModel.decks.value.isNotEmpty())
        org.junit.Assert.assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun setLanguage_requeriesDecksForThatLanguage() = runTest {
        whenever(repository.getDecks(any())).thenReturn(flowOf(emptyList()))
        // Pin the initial language so this test is independent of host/device locale.
        viewModel = DeckViewModel(repository, syncRepository, initialLanguage = "en")
        advanceUntilIdle()

        viewModel.setLanguage("ru")
        advanceUntilIdle()

        verify(repository).getDecks(eq("en"))
        verify(repository).getDecks(eq("ru"))
    }

    @Test
    fun loadDeckStats_resetsCountsBeforeLoading() = runTest {
        whenever(repository.getDecks(any())).thenReturn(flowOf(emptyList()))
        whenever(repository.getDeckFlow(DECK_ID)).thenReturn(flowOf(null))
        whenever(repository.getCards(DECK_ID)).thenReturn(flowOf(emptyList()))
        whenever(repository.getDueCardCount(DECK_ID)).thenReturn(flowOf(3))

        viewModel = DeckViewModel(repository, syncRepository)
        advanceUntilIdle()

        viewModel.loadDeckStats(DECK_ID)

        // Counts reset synchronously, before the collectors run.
        org.junit.Assert.assertEquals(0, viewModel.selectedDeckCardCount.value)
        org.junit.Assert.assertEquals(0, viewModel.selectedDeckDueCount.value)
    }

    @Test
    fun loadDeckStats_derivesCardCountFromCardList() = runTest {
        val cards = listOf(
            Card(deckId = DECK_ID, front = "hola", back = "привет"),
            Card(deckId = DECK_ID, front = "adéu", back = "пока")
        )
        whenever(repository.getDecks(any())).thenReturn(flowOf(emptyList()))
        whenever(repository.getDeckFlow(DECK_ID)).thenReturn(flowOf(Deck(id = DECK_ID, name = "Test")))
        whenever(repository.getCards(DECK_ID)).thenReturn(flowOf(cards))
        whenever(repository.getDueCardCount(DECK_ID)).thenReturn(flowOf(0))

        viewModel = DeckViewModel(repository, syncRepository)
        advanceUntilIdle()

        viewModel.loadDeckStats(DECK_ID)
        advanceUntilIdle()

        org.junit.Assert.assertEquals(2, viewModel.selectedDeckCardCount.value)
        org.junit.Assert.assertEquals(2, viewModel.selectedDeckCards.value.size)
    }

    @Test
    fun createCard_delegatesToRepository() = runTest {
        whenever(repository.getDecks(any())).thenReturn(flowOf(emptyList()))
        viewModel = DeckViewModel(repository, syncRepository)
        advanceUntilIdle()

        viewModel.createCard(DECK_ID, "  hola  ", "  привет  ")
        advanceUntilIdle()

        // Fields are trimmed before reaching the repository.
        verify(repository).createCard(DECK_ID, "hola", "привет")
    }

    @Test
    fun updateCard_trimsAndDelegatesToRepository() = runTest {
        whenever(repository.getDecks(any())).thenReturn(flowOf(emptyList()))
        viewModel = DeckViewModel(repository, syncRepository)
        advanceUntilIdle()

        val card = Card(deckId = DECK_ID, front = "old", back = "старое")
        viewModel.updateCard(card, "  new  ", "  новое  ")
        advanceUntilIdle()

        // Only id + trimmed text reach the repository; scheduling fields are
        // re-read there, not copied from the (possibly stale) snapshot.
        verify(repository).updateCardContent(card.id, "new", "новое")
    }

    @Test
    fun deleteCard_delegatesToRepository() = runTest {
        whenever(repository.getDecks(any())).thenReturn(flowOf(emptyList()))
        viewModel = DeckViewModel(repository, syncRepository)
        advanceUntilIdle()

        val card = Card(deckId = DECK_ID, front = "hola", back = "привет")
        viewModel.deleteCard(card)
        advanceUntilIdle()

        verify(repository).deleteCard(card)
    }

    @Test
    fun createDeck_setIsLoadingFalseOnSuccess() = runTest {
        whenever(repository.getDecks(any())).thenReturn(flowOf(emptyList()))
        whenever(repository.createDeck("Test")).thenReturn(DECK_ID)

        viewModel = DeckViewModel(repository, syncRepository)
        advanceUntilIdle()

        viewModel.createDeck("Test")
        advanceUntilIdle()

        org.junit.Assert.assertFalse(viewModel.isLoading.value)
    }
}
