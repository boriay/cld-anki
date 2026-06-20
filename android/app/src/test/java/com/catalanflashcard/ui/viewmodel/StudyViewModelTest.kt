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

    companion object {
        const val DECK_ID = "deck-uuid-1"
        const val CARD_ID_1 = "card-uuid-1"
        const val CARD_ID_2 = "card-uuid-2"
    }

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
            Card(id = CARD_ID_1, deckId = DECK_ID, front = "Front1", back = "Back1"),
            Card(id = CARD_ID_2, deckId = DECK_ID, front = "Front2", back = "Back2")
        )
        whenever(repository.getDueCards(DECK_ID)).thenReturn(cards)

        viewModel.loadDueCards(DECK_ID)
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
        val card1 = Card(id = CARD_ID_1, deckId = DECK_ID, front = "Front", back = "Back")
        val card2 = Card(id = CARD_ID_2, deckId = DECK_ID, front = "Front2", back = "Back2")

        whenever(repository.getDueCards(DECK_ID)).thenReturn(listOf(card1, card2))
        whenever(repository.updateCardReview(any(), any())).thenReturn(Unit)

        viewModel.loadDueCards(DECK_ID)
        advanceUntilIdle()

        // Call answerCard twice rapidly to test double-tap guard
        viewModel.answerCard(Quality.GOOD)
        viewModel.answerCard(Quality.GOOD)

        advanceUntilIdle()

        // Verify updateCardReview was only called once despite two answerCard calls
        verify(repository, times(1)).updateCardReview(CARD_ID_1, Quality.GOOD.value)
        org.junit.Assert.assertFalse(viewModel.isSavingAnswer.value)
    }
}
