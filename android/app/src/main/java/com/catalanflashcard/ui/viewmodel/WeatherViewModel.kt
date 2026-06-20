package com.catalanflashcard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catalanflashcard.data.repository.WeatherRepository
import com.catalanflashcard.data.repository.WeatherState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the app-wide weather background. Starts from a neutral default and then
 * loads the cached/fresh state off the main thread (the repository reads
 * SharedPreferences and the network on Dispatchers.IO), respecting the
 * once-per-hour cap. Avoids disk I/O on the main thread during composition.
 */
class WeatherViewModel(private val repository: WeatherRepository) : ViewModel() {
    private val _state = MutableStateFlow(WeatherState())
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = repository.refresh()
        }
    }
}
