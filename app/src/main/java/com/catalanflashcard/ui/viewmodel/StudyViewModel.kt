package com.catalanflashcard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.catalanflashcard.data.entity.Card
import com.catalanflashcard.data.repository.FlashcardRepository
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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadDueCards(deckId: Long) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val cards = repository.getDueCards(deckId)
                _dueCards.value = cards
                _currentIndex.value = 0
                _currentCard.value = cards.firstOrNull()
                _isFlipped.value = false
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load cards"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun flipCard() {
        _isFlipped.value = !_isFlipped.value
    }

    fun answerCard(quality: Int) {
        val card = _currentCard.value ?: return

        viewModelScope.launch {
            try {
                repository.updateCardReview(card.id, quality)

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
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

class StudyViewModelFactory(private val repository: FlashcardRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StudyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StudyViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
