package com.catalanflashcard.ui.viewmodel

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
        whenever(repository.getAllDecks()).thenReturn(flowOf(decks))

        viewModel = DeckViewModel(repository, syncRepository)
        advanceUntilIdle()

        org.junit.Assert.assertTrue(viewModel.decks.value.isNotEmpty())
        org.junit.Assert.assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun loadDeckStats_resetsCountsBeforeLoading() = runTest {
        whenever(repository.getAllDecks()).thenReturn(flowOf(emptyList()))
        whenever(repository.getCardCount(DECK_ID)).thenReturn(flowOf(5))
        whenever(repository.getDueCardCount(DECK_ID)).thenReturn(flowOf(3))

        viewModel = DeckViewModel(repository, syncRepository)
        advanceUntilIdle()

        viewModel.loadDeckStats(DECK_ID)

        org.junit.Assert.assertEquals(0, viewModel.selectedDeckCardCount.value)
        org.junit.Assert.assertEquals(0, viewModel.selectedDeckDueCount.value)
    }

    @Test
    fun createDeck_setIsLoadingFalseOnSuccess() = runTest {
        whenever(repository.getAllDecks()).thenReturn(flowOf(emptyList()))
        whenever(repository.createDeck("Test")).thenReturn(DECK_ID)

        viewModel = DeckViewModel(repository, syncRepository)
        advanceUntilIdle()

        viewModel.createDeck("Test")
        advanceUntilIdle()

        org.junit.Assert.assertFalse(viewModel.isLoading.value)
    }
}
