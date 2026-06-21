package com.catalanflashcard.data.sync

import com.catalanflashcard.data.database.LocalSeeder
import com.catalanflashcard.data.preferences.AutoSyncSettings
import com.catalanflashcard.data.repository.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {

    private class FakeSettings(override var autoSyncEnabled: Boolean) : AutoSyncSettings

    // Unconfined: launched coroutines (the immediate sync) run eagerly without a
    // manual advance, while the debounce delay stays virtual on the testScheduler.
    private fun unconfinedScope(testScheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    @Test
    fun enabling_triggersImmediateSync() = runTest {
        val repo = mock<SyncRepository>()
        whenever(repo.sync()).thenReturn(Result.success(Unit))
        val settings = FakeSettings(false)
        val scope = unconfinedScope(testScheduler)
        val manager = SyncManager(repo, settings, mock<LocalSeeder>(), scope)

        manager.toggle() // enable
        advanceUntilIdle()

        verify(repo, times(1)).sync()
        assertTrue(settings.autoSyncEnabled)
        scope.cancel()
    }

    @Test
    fun disabling_blocksPendingDebounceSync() = runTest {
        val repo = mock<SyncRepository>()
        whenever(repo.sync()).thenReturn(Result.success(Unit))
        val settings = FakeSettings(false)
        val scope = unconfinedScope(testScheduler)
        val manager = SyncManager(repo, settings, mock<LocalSeeder>(), scope)

        manager.toggle()      // enable -> immediate sync (1)
        advanceUntilIdle()

        manager.requestSync() // queues a pending (debounced) sync
        manager.toggle()      // disable before the debounce window elapses
        advanceUntilIdle()

        // Only the immediate enable-sync ran; the pending one was gated off by the enabled check.
        verify(repo, times(1)).sync()
        scope.cancel()
    }

    @Test
    fun batchedRequests_coalesceIntoSingleSync() = runTest {
        val repo = mock<SyncRepository>()
        whenever(repo.sync()).thenReturn(Result.success(Unit))
        val settings = FakeSettings(true) // enabled from the start
        val scope = unconfinedScope(testScheduler)
        val manager = SyncManager(repo, settings, mock<LocalSeeder>(), scope)
        advanceUntilIdle() // startup sync (1)

        // Five rapid edits within the debounce window collapse into a single sync.
        repeat(5) { manager.requestSync() }
        advanceUntilIdle()

        verify(repo, times(2)).sync() // 1 startup + 1 coalesced
        scope.cancel()
    }

    @Test
    fun syncFailure_isReportedToErrors() = runTest {
        val repo = mock<SyncRepository>()
        whenever(repo.sync()).thenReturn(Result.failure(RuntimeException("boom")))
        val settings = FakeSettings(false)
        val scope = unconfinedScope(testScheduler)
        val manager = SyncManager(repo, settings, mock<LocalSeeder>(), scope)

        val received = mutableListOf<String>()
        scope.launch { manager.errors.collect { received += it } }

        manager.toggle() // enable -> sync -> failure
        advanceUntilIdle()

        assertEquals(listOf("boom"), received)
        scope.cancel()
    }

    @Test
    fun requestSync_whenDisabled_doesNothing() = runTest {
        val repo = mock<SyncRepository>()
        val settings = FakeSettings(false)
        val scope = unconfinedScope(testScheduler)
        val manager = SyncManager(repo, settings, mock<LocalSeeder>(), scope)

        manager.requestSync()
        advanceUntilIdle()

        verifyNoInteractions(repo)
        scope.cancel()
    }
}
