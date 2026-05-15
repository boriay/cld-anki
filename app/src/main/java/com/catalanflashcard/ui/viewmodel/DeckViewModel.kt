package com.catalanflashcard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.catalanflashcard.data.entity.Deck
import com.catalanflashcard.data.repository.FlashcardRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeckViewModel(private val repository: FlashcardRepository) : ViewModel() {
    private val _decks = MutableStateFlow<List<Deck>>(emptyList())
    val decks: StateFlow<List<Deck>> = _decks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedDeckCardCount = MutableStateFlow(0)
    val selectedDeckCardCount: StateFlow<Int> = _selectedDeckCardCount.asStateFlow()

    private val _selectedDeckDueCount = MutableStateFlow(0)
    val selectedDeckDueCount: StateFlow<Int> = _selectedDeckDueCount.asStateFlow()

    private var statsJob: Job? = null

    init {
        loadDecks()
    }

    private fun loadDecks() {
        viewModelScope.launch {
            repository.getAllDecks().collect { deckList ->
                _decks.value = deckList
            }
        }
    }

    fun loadDeckStats(deckId: Long) {
        statsJob?.cancel()
        _selectedDeckCardCount.value = 0
        _selectedDeckDueCount.value = 0
        statsJob = viewModelScope.launch {
            launch {
                repository.getCardCount(deckId).collect { _selectedDeckCardCount.value = it }
            }
            launch {
                repository.getDueCardCount(deckId).collect { _selectedDeckDueCount.value = it }
            }
        }
    }

    fun createDeck(name: String, description: String = "") {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.createDeck(name, description)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to create deck"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteDeck(deckId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteDeck(deckId)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete deck"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

class DeckViewModelFactory(private val repository: FlashcardRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeckViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DeckViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
