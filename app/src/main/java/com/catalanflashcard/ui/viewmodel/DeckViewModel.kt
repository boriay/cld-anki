package com.catalanflashcard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catalanflashcard.data.entity.Deck
import com.catalanflashcard.data.repository.FlashcardRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DeckViewModel(private val repository: FlashcardRepository) : ViewModel() {
    private val _decks = MutableStateFlow<List<Deck>>(emptyList())
    val decks: StateFlow<List<Deck>> = _decks.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedDeck = MutableStateFlow<Deck?>(null)
    val selectedDeck: StateFlow<Deck?> = _selectedDeck.asStateFlow()

    private val _isLoadingDeck = MutableStateFlow(false)
    val isLoadingDeck: StateFlow<Boolean> = _isLoadingDeck.asStateFlow()

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
            _isLoading.value = true
            repository.getAllDecks()
                .catch { e ->
                    _error.value = e.message ?: "Failed to load decks"
                    _isLoading.value = false
                }
                .collect { deckList ->
                    _decks.value = deckList
                    _isLoading.value = false
                }
        }
    }

    fun loadDeckStats(deckId: Long) {
        statsJob?.cancel()
        _selectedDeck.value = null
        _isLoadingDeck.value = true
        _selectedDeckCardCount.value = 0
        _selectedDeckDueCount.value = 0
        statsJob = viewModelScope.launch {
            launch {
                repository.getDeckFlow(deckId)
                    .catch { e ->
                        _error.value = e.message ?: "Failed to load deck"
                        _isLoadingDeck.value = false
                    }
                    .collect { deck ->
                        _selectedDeck.value = deck
                        _isLoadingDeck.value = false
                    }
            }
            combine(
                repository.getCardCount(deckId),
                repository.getDueCardCount(deckId)
            ) { total, due ->
                _selectedDeckCardCount.value = total
                _selectedDeckDueCount.value = due
            }
                .catch { e -> _error.value = e.message ?: "Failed to load stats" }
                .collect()
        }
    }

    fun clearDeckStats() {
        statsJob?.cancel()
        _selectedDeck.value = null
        _isLoadingDeck.value = false
        _selectedDeckCardCount.value = 0
        _selectedDeckDueCount.value = 0
    }

    fun createDeck(name: String, description: String = "") {
        // No manual _isLoading toggling here: the decks Flow re-emits after insert and
        // drives the loading state, avoiding a race with loadDecks' collector.
        viewModelScope.launch {
            try {
                repository.createDeck(name, description)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to create deck"
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
