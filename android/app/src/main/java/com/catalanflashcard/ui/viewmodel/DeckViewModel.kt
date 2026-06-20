package com.catalanflashcard.ui.viewmodel

import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catalanflashcard.data.entity.Card
import com.catalanflashcard.data.entity.Deck
import com.catalanflashcard.data.repository.FlashcardRepository
import com.catalanflashcard.data.sync.SyncController
import com.catalanflashcard.ui.resolveAppLanguage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.util.Locale

class DeckViewModel(
    private val repository: FlashcardRepository,
    private val syncController: SyncController,
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

    private val _selectedDeckCards = MutableStateFlow<List<Card>>(emptyList())
    val selectedDeckCards: StateFlow<List<Card>> = _selectedDeckCards.asStateFlow()

    // Индикатор и тумблер авто-синка общие на все экраны — берём из SyncController.
    val isSyncing: StateFlow<Boolean> = syncController.isSyncing
    val autoSyncEnabled: StateFlow<Boolean> = syncController.enabled

    private var statsJob: Job? = null

    init {
        loadDecks()
        // Ошибки фоновой синхронизации показываем тем же снэкбаром, что и прочие.
        viewModelScope.launch {
            syncController.errors.collect { msg -> _error.value = msg }
        }
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
        _selectedDeckCards.value = emptyList()
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
            // The full card list backs both the editor UI and the total count,
            // so derive the count from it instead of issuing a separate COUNT query.
            launch {
                repository.getCards(deckId)
                    .catch { e -> _error.value = e.message ?: "Failed to load cards" }
                    .collect { cards ->
                        _selectedDeckCards.value = cards
                        _selectedDeckCardCount.value = cards.size
                    }
            }
            // Due count keeps its own index-backed COUNT query (filters on
            // nextReviewTime), separate from the loaded card rows.
            launch {
                repository.getDueCardCount(deckId)
                    .catch { e -> _error.value = e.message ?: "Failed to load stats" }
                    .collect { due -> _selectedDeckDueCount.value = due }
            }
        }
    }

    fun clearDeckStats() {
        statsJob?.cancel()
        _selectedDeck.value = null
        _isLoadingDeck.value = false
        _selectedDeckCardCount.value = 0
        _selectedDeckDueCount.value = 0
        _selectedDeckCards.value = emptyList()
    }

    fun createDeck(name: String) {
        // No manual _isLoading toggling here: the decks Flow re-emits after insert and
        // drives the loading state, avoiding a race with loadDecks' collector.
        viewModelScope.launch {
            try {
                repository.createDeck(name)
                _error.value = null
                syncController.requestSync()
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
                syncController.requestSync()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete deck"
            }
        }
    }

    fun createCard(deckId: String, front: String, back: String) {
        viewModelScope.launch {
            try {
                repository.createCard(deckId, front.trim(), back.trim())
                _error.value = null
                syncController.requestSync()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to create card"
            }
        }
    }

    fun updateCard(card: Card, front: String, back: String) {
        viewModelScope.launch {
            try {
                repository.updateCardContent(card.id, front.trim(), back.trim())
                _error.value = null
                syncController.requestSync()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to update card"
            }
        }
    }

    fun deleteCard(card: Card) {
        viewModelScope.launch {
            try {
                repository.deleteCard(card)
                _error.value = null
                syncController.requestSync()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete card"
            }
        }
    }

    // Переключатель авто-синка. Включение принудительно запускает синк сразу,
    // дальше синк идёт сам после каждой правки (с debounce). Состояние и
    // конкурентность держит SyncController — он общий на все экраны.
    fun toggleAutoSync() {
        syncController.toggle()
    }

    fun clearError() {
        _error.value = null
    }
}
