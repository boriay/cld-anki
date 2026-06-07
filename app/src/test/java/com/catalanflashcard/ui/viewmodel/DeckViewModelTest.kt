package com.catalanflashcard.ui.viewmodel

import com.catalanflashcard.data.entity.Deck
import com.catalanflashcard.data.repository.FlashcardRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class DeckViewModelTest {
    @Mock private lateinit var repository: FlashcardRepository
    private lateinit var viewModel: DeckViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun init_loadsDecks() = runTest(testDispatcher) {
        val decks = listOf(Deck(id = 1, name = "Test"))
        whenever(repository.getAllDecks()).thenReturn(flowOf(decks))

        viewModel = DeckViewModel(repository)

        org.junit.Assert.assertTrue(viewModel.decks.value.isNotEmpty())
    }

    @Test
    fun loadDeckStats_resetsCountsBeforeLoading() = runTest(testDispatcher) {
        val decks = emptyList<Deck>()
        whenever(repository.getAllDecks()).thenReturn(flowOf(decks))
        whenever(repository.getCardCount(1)).thenReturn(flowOf(5))
        whenever(repository.getDueCardCount(1)).thenReturn(flowOf(3))

        viewModel = DeckViewModel(repository)
        viewModel.loadDeckStats(1)

        org.junit.Assert.assertEquals(0, viewModel.selectedDeckCardCount.value)
        org.junit.Assert.assertEquals(0, viewModel.selectedDeckDueCount.value)
    }

    @Test
    fun createDeck_setIsLoadingFalseOnSuccess() = runTest(testDispatcher) {
        val decks = emptyList<Deck>()
        whenever(repository.getAllDecks()).thenReturn(flowOf(decks))
        whenever(repository.createDeck("Test", "Desc")).thenReturn(1)

        viewModel = DeckViewModel(repository)
        val initialLoading = viewModel.isLoading.value
        viewModel.createDeck("Test", "Desc")

        org.junit.Assert.assertFalse(viewModel.isLoading.value)
    }
}
