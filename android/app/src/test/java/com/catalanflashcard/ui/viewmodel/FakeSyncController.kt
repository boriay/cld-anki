package com.catalanflashcard.ui.viewmodel

import com.catalanflashcard.data.sync.SyncController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lightweight fake instead of a Mockito mock: exposes real Flows so collecting
 * [SyncController.errors] in a ViewModel's init doesn't crash with an NPE. Counts
 * requestSync/toggle calls for assertions when needed.
 */
class FakeSyncController : SyncController {
    override val isSyncing = MutableStateFlow(false)
    override val enabled = MutableStateFlow(false)
    override val errors = MutableSharedFlow<String>()

    var requestSyncCount = 0
        private set
    var toggleCount = 0
        private set
    var syncNowCount = 0
        private set
    var resyncFromScratchCount = 0
        private set

    override fun requestSync() {
        requestSyncCount++
    }

    override fun toggle() {
        toggleCount++
        enabled.value = !enabled.value
    }

    override fun syncNow() {
        syncNowCount++
    }

    override suspend fun prepareAccountSwitch() {}

    override suspend fun resyncFromScratch() {
        resyncFromScratchCount++
    }
}
