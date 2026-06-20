package com.catalanflashcard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catalanflashcard.data.repository.WeatherRepository
import com.catalanflashcard.data.repository.WeatherState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the app-wide weather background. Starts from a neutral default, then
 * emits the cached state and refreshes — all off the main thread (the
 * repository reads SharedPreferences and the network on Dispatchers.IO).
 * Emitting the cache first restores the right scene near-instantly on cold
 * start without doing disk I/O during composition.
 */
class WeatherViewModel(private val repository: WeatherRepository) : ViewModel() {
    private val _state = MutableStateFlow(WeatherState())
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // cached() touches SharedPreferences; keep it off the main thread.
            _state.value = withContext(Dispatchers.IO) { repository.cached() }
            _state.value = repository.refresh() // then fetch if the cache is stale
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = repository.refresh()
        }
    }
}
