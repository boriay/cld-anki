package com.catalanflashcard.ui.viewmodel

import com.catalanflashcard.data.entity.Deck
import com.catalanflashcard.data.repository.FlashcardRepository
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
    private lateinit var viewModel: DeckViewModel
    private val testDispatcher = StandardTestDispatcher()

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
        val decks = listOf(Deck(id = 1, name = "Test"))
        whenever(repository.getAllDecks()).thenReturn(flowOf(decks))

        viewModel = DeckViewModel(repository)
        advanceUntilIdle()

        org.junit.Assert.assertTrue(viewModel.decks.value.isNotEmpty())
        org.junit.Assert.assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun loadDeckStats_resetsCountsBeforeLoading() = runTest {
        val decks = emptyList<Deck>()
        whenever(repository.getAllDecks()).thenReturn(flowOf(decks))
        whenever(repository.getCardCount(1)).thenReturn(flowOf(5))
        whenever(repository.getDueCardCount(1)).thenReturn(flowOf(3))

        viewModel = DeckViewModel(repository)
        advanceUntilIdle()

        viewModel.loadDeckStats(1)

        org.junit.Assert.assertEquals(0, viewModel.selectedDeckCardCount.value)
        org.junit.Assert.assertEquals(0, viewModel.selectedDeckDueCount.value)
    }

    @Test
    fun createDeck_setIsLoadingFalseOnSuccess() = runTest {
        val decks = emptyList<Deck>()
        whenever(repository.getAllDecks()).thenReturn(flowOf(decks))
        whenever(repository.createDeck("Test", "Desc")).thenReturn(1)

        viewModel = DeckViewModel(repository)
        advanceUntilIdle()

        viewModel.createDeck("Test", "Desc")
        advanceUntilIdle()

        org.junit.Assert.assertFalse(viewModel.isLoading.value)
    }
}
