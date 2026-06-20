package com.catalanflashcard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catalanflashcard.data.repository.WeatherRepository
import com.catalanflashcard.data.repository.WeatherState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the app-wide weather background. Starts from a neutral default, emits
 * the cached state, then refreshes — all off the main thread (the repository
 * reads SharedPreferences and the network on Dispatchers.IO). Emitting the cache
 * first restores the right scene near-instantly on cold start without disk I/O
 * during composition.
 *
 * [refresh] is also called on each lifecycle ON_START so day/night and the
 * forecast update when the app returns from the background after the cache TTL,
 * not only when the Activity/process is recreated. An in-flight guard coalesces
 * overlapping calls (the repository's own 1h cap then bounds upstream traffic).
 */
class WeatherViewModel(private val repository: WeatherRepository) : ViewModel() {
    private val _state = MutableStateFlow(WeatherState())
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            // cached() touches SharedPreferences; keep it off the main thread.
            _state.value = withContext(Dispatchers.IO) { repository.cached() }
        }
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.value = repository.refresh()
        }
    }
}
