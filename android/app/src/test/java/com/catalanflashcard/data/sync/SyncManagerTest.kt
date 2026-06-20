package com.catalanflashcard.data.sync

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

    // Unconfined: launched-корутины (немедленный синк) исполняются жадно, без
    // ручного advance, а delay в debounce остаётся виртуальным на testScheduler.
    private fun unconfinedScope(testScheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    @Test
    fun enabling_triggersImmediateSync() = runTest {
        val repo = mock<SyncRepository>()
        whenever(repo.sync()).thenReturn(Result.success(Unit))
        val settings = FakeSettings(false)
        val scope = unconfinedScope(testScheduler)
        val manager = SyncManager(repo, settings, scope)

        manager.toggle() // включение
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
        val manager = SyncManager(repo, settings, scope)

        manager.toggle()      // включение -> немедленный синк (1)
        advanceUntilIdle()

        manager.requestSync() // ставит отложенный (debounce) синк в очередь
        manager.toggle()      // выключаем до истечения окна debounce
        advanceUntilIdle()

        // Сработал только немедленный синк включения; отложенный погашен проверкой enabled.
        verify(repo, times(1)).sync()
        scope.cancel()
    }

    @Test
    fun batchedRequests_coalesceIntoSingleSync() = runTest {
        val repo = mock<SyncRepository>()
        whenever(repo.sync()).thenReturn(Result.success(Unit))
        val settings = FakeSettings(true) // включено с самого старта
        val scope = unconfinedScope(testScheduler)
        val manager = SyncManager(repo, settings, scope)
        advanceUntilIdle() // стартовый синк (1)

        // Пять быстрых правок в пределах окна debounce схлопываются в один синк.
        repeat(5) { manager.requestSync() }
        advanceUntilIdle()

        verify(repo, times(2)).sync() // 1 стартовый + 1 коалесцированный
        scope.cancel()
    }

    @Test
    fun syncFailure_isReportedToErrors() = runTest {
        val repo = mock<SyncRepository>()
        whenever(repo.sync()).thenReturn(Result.failure(RuntimeException("boom")))
        val settings = FakeSettings(false)
        val scope = unconfinedScope(testScheduler)
        val manager = SyncManager(repo, settings, scope)

        val received = mutableListOf<String>()
        scope.launch { manager.errors.collect { received += it } }

        manager.toggle() // включение -> синк -> ошибка
        advanceUntilIdle()

        assertEquals(listOf("boom"), received)
        scope.cancel()
    }

    @Test
    fun requestSync_whenDisabled_doesNothing() = runTest {
        val repo = mock<SyncRepository>()
        val settings = FakeSettings(false)
        val scope = unconfinedScope(testScheduler)
        val manager = SyncManager(repo, settings, scope)

        manager.requestSync()
        advanceUntilIdle()

        verifyNoInteractions(repo)
        scope.cancel()
    }
}
