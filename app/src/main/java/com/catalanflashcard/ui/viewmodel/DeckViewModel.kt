package com.catalanflashcard.ui.viewmodel

import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catalanflashcard.data.entity.Deck
import com.catalanflashcard.data.repository.FlashcardRepository
import com.catalanflashcard.data.repository.SyncRepository
import com.catalanflashcard.ui.resolveAppLanguage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.util.Locale

class DeckViewModel(
    private val repository: FlashcardRepository,
    private val syncRepository: SyncRepository,
    initialLanguage: String = resolveAppLanguage(
        AppCompatDelegate.getApplicationLocales().get(0) ?: Locale.getDefault()
    )
) : ViewModel() {
    private val _decks = MutableStateFlow<List<Deck>>(emptyList())
    val decks: StateFlow<List<Deck>> = _decks.asStateFlow()

    private val _language = MutableStateFlow(initialLanguage)

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

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private var statsJob: Job? = null

    init {
        loadDecks()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadDecks() {
        viewModelScope.launch {
            _isLoading.value = true
            _language
                .flatMapLatest { lang ->
                    // .catch is inside flatMapLatest so an error on one language's
                    // query terminates only that inner Flow. The outer _language flow
                    // stays alive; setLanguage() can still trigger a fresh re-query.
                    repository.getDecks(lang)
                        .catch { e ->
                            _error.value = e.message ?: "Failed to load decks"
                            _isLoading.value = false
                        }
                }
                .collect { deckList ->
                    _decks.value = deckList
                    _isLoading.value = false
                }
        }
    }

    // Called by the screen with the language resolved from the current locale,
    // and by the in-app language menu. Re-queries the deck list via flatMapLatest.
    fun setLanguage(language: String) {
        _language.value = language
    }

    fun loadDeckStats(deckId: String) {
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

    fun createDeck(name: String) {
        // No manual _isLoading toggling here: the decks Flow re-emits after insert and
        // drives the loading state, avoiding a race with loadDecks' collector.
        viewModelScope.launch {
            try {
                repository.createDeck(name)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to create deck"
            }
        }
    }

    fun deleteDeck(deckId: String) {
        viewModelScope.launch {
            try {
                repository.deleteDeck(deckId)
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete deck"
            }
        }
    }

    fun sync() {
        // Set the guard synchronously before launching: if it were set inside the
        // coroutine, two rapid taps could both pass the check before either runs.
        if (_isSyncing.value) return
        _isSyncing.value = true
        viewModelScope.launch {
            try {
                syncRepository.sync().onFailure { e ->
                    _error.value = e.message ?: "Sync failed"
                }
            } finally {
                // Reset even if the coroutine is cancelled, so the UI never sticks.
                _isSyncing.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
