package com.catalanflashcard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catalanflashcard.data.entity.Card
import com.catalanflashcard.data.repository.FlashcardRepository
import com.catalanflashcard.domain.Quality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StudyViewModel(private val repository: FlashcardRepository) : ViewModel() {
    private val _currentCard = MutableStateFlow<Card?>(null)
    val currentCard: StateFlow<Card?> = _currentCard.asStateFlow()

    private val _dueCards = MutableStateFlow<List<Card>>(emptyList())
    val dueCards: StateFlow<List<Card>> = _dueCards.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isFlipped = MutableStateFlow(false)
    val isFlipped: StateFlow<Boolean> = _isFlipped.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isSavingAnswer = MutableStateFlow(false)
    val isSavingAnswer: StateFlow<Boolean> = _isSavingAnswer.asStateFlow()

    fun loadDueCards(deckId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val cards = repository.getDueCards(deckId)
                _dueCards.value = cards
                _currentIndex.value = 0
                _currentCard.value = cards.firstOrNull()
                _isFlipped.value = false
                _error.value = null
            } catch (e: Exception) {
                _dueCards.value = emptyList()
                _currentCard.value = null
                _currentIndex.value = 0
                _isFlipped.value = false
                _error.value = e.message ?: "Failed to load cards"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun flipCard() {
        _isFlipped.value = !_isFlipped.value
    }

    fun answerCard(quality: Quality) {
        if (_isSavingAnswer.value) return
        val card = _currentCard.value ?: return
        // Set the guard synchronously on the main thread before launching so rapid
        // double-taps can't enqueue multiple coroutines for the same card.
        _isSavingAnswer.value = true

        viewModelScope.launch {
            try {
                repository.updateCardReview(card.id, quality.value)

                val currentIdx = _currentIndex.value
                if (currentIdx < _dueCards.value.size - 1) {
                    _currentIndex.value = currentIdx + 1
                    _currentCard.value = _dueCards.value[currentIdx + 1]
                    _isFlipped.value = false
                } else {
                    _currentCard.value = null
                    _currentIndex.value = 0
                    _dueCards.value = emptyList()
                }
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to save answer"
            } finally {
                _isSavingAnswer.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
