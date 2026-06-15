package com.catalanflashcard.ui.viewmodel

import com.catalanflashcard.data.entity.Card
import com.catalanflashcard.data.repository.FlashcardRepository
import com.catalanflashcard.domain.Quality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.times
import org.mockito.kotlin.any

@OptIn(ExperimentalCoroutinesApi::class)
class StudyViewModelTest {
    @Mock private lateinit var repository: FlashcardRepository
    private lateinit var viewModel: StudyViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        viewModel = StudyViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadDueCards_setsCardsAndCurrentCard() = runTest {
        val cards = listOf(
            Card(id = 1, deckId = 1, front = "Front1", back = "Back1"),
            Card(id = 2, deckId = 1, front = "Front2", back = "Back2")
        )
        whenever(repository.getDueCards(1)).thenReturn(cards)

        viewModel.loadDueCards(1)
        advanceUntilIdle()

        org.junit.Assert.assertEquals(cards, viewModel.dueCards.value)
        org.junit.Assert.assertEquals(cards[0], viewModel.currentCard.value)
    }

    @Test
    fun flipCard_togglesFlipped() {
        org.junit.Assert.assertFalse(viewModel.isFlipped.value)
        viewModel.flipCard()
        org.junit.Assert.assertTrue(viewModel.isFlipped.value)
        viewModel.flipCard()
        org.junit.Assert.assertFalse(viewModel.isFlipped.value)
    }

    @Test
    fun answerCard_preventsDoubleTap() = runTest {
        val card = Card(id = 1, deckId = 1, front = "Front", back = "Back")
        val card2 = Card(id = 2, deckId = 1, front = "Front2", back = "Back2")

        whenever(repository.getDueCards(1)).thenReturn(listOf(card, card2))
        whenever(repository.updateCardReview(any(), any())).thenReturn(Unit)

        viewModel.loadDueCards(1)
        advanceUntilIdle()

        // Call answerCard twice rapidly to test double-tap guard
        viewModel.answerCard(Quality.GOOD)
        viewModel.answerCard(Quality.GOOD)

        // Advance scheduler to allow coroutines to run
        advanceUntilIdle()

        // Verify updateCardReview was only called once despite two answerCard calls
        verify(repository, times(1)).updateCardReview(1, 4)
        org.junit.Assert.assertFalse(viewModel.isSavingAnswer.value)
    }
}
