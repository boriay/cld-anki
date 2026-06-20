package com.catalanflashcard.ui.viewmodel

import com.catalanflashcard.data.sync.SyncController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Лёгкий фейк вместо Mockito-мока: отдаёт реальные Flow, чтобы сбор
 * [SyncController.errors] в init вьюмодели не падал с NPE. Считает вызовы
 * requestSync/toggle для проверок при необходимости.
 */
class FakeSyncController : SyncController {
    override val isSyncing = MutableStateFlow(false)
    override val enabled = MutableStateFlow(false)
    override val errors = MutableSharedFlow<String>()

    var requestSyncCount = 0
        private set
    var toggleCount = 0
        private set

    override fun requestSync() {
        requestSyncCount++
    }

    override fun toggle() {
        toggleCount++
        enabled.value = !enabled.value
    }
}
