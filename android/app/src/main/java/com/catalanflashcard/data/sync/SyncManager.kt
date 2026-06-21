package com.catalanflashcard.data.sync

import android.content.Context
import com.catalanflashcard.data.database.FlashcardDatabase
import com.catalanflashcard.data.preferences.AutoSyncSettings
import com.catalanflashcard.data.preferences.SyncPreferences
import com.catalanflashcard.data.repository.SyncRepository
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Drives auto-sync: the shared on/off toggle, the "syncing now" indicator and
 * the debounce engine that coalesces a burst of edits into a single server call.
 *
 * Kept as a singleton (not a ViewModel) because DeckViewModel is created per
 * back-stack entry and StudyViewModel is separate — the sync state must be
 * shared across all screens.
 */
interface SyncController {
    /** Whether a sync is running right now (for the UI indicator). */
    val isSyncing: StateFlow<Boolean>

    /** Whether auto-sync is enabled. Off by default. */
    val enabled: StateFlow<Boolean>

    /** Sync error messages to surface in the UI. */
    val errors: SharedFlow<String>

    /** Request a sync after a local edit. No-op while auto-sync is off. */
    fun requestSync()

    /** Toggle auto-sync. Enabling forces an immediate sync. */
    fun toggle()

    /** Force a one-off sync now, regardless of the auto-sync toggle. */
    fun syncNow()

    /**
     * Cancel any in-flight regular sync and invalidate pending ones. Must be
     * called BEFORE Firebase sign-in so no running sync can acquire the new
     * UID's token while still holding the old account's data in the local DB.
     */
    suspend fun prepareAccountSwitch()

    /**
     * Wipe local data, reset the sync cursor, and pull the current account's
     * data from scratch. Suspends until the full re-pull completes so the
     * caller (AuthViewModel) can keep the busy indicator up the whole time.
     */
    suspend fun resyncFromScratch()
}

class SyncManager internal constructor(
    private val syncRepository: SyncRepository,
    private val settings: AutoSyncSettings,
    // Application scope: survives Activity recreation (e.g. a locale change).
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : SyncController {

    private val _isSyncing = MutableStateFlow(false)
    override val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _enabled = MutableStateFlow(settings.autoSyncEnabled)
    override val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    // extraBufferCapacity=1 + DROP_OLDEST: a burst of edits collapses into one
    // tick, and tryEmit never blocks the caller.
    private val _errors = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val requests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Serializes sync runs: a debounce tick that arrives while a sync is running
    // waits and re-syncs, picking up rows changed during the in-flight sync.
    private val mutex = Mutex()

    // Incremented by prepareAccountSwitch() and resyncFromScratch(). A regular
    // sync captures the generation before the lock; if it changed by the time
    // the lock is acquired, the sync is stale and is dropped.
    private val syncGeneration = AtomicInteger(0)

    // Tracks the Job of the most recently launched regular (non-resync) sync so
    // prepareAccountSwitch() can cancel it before Firebase switches the UID.
    private val regularSyncJob = AtomicReference<Job?>(null)

    private fun launchRegularSync(gen: Int) {
        regularSyncJob.set(scope.launch { runSync(gen) })
    }

    @OptIn(FlowPreview::class)
    private fun startDebounceLoop() {
        scope.launch {
            requests
                .debounce(DEBOUNCE_MS)
                // Re-check enabled: if auto-sync was turned off while the tick was
                // waiting out the debounce window, drop the pending request instead
                // of pushing it to the server.
                .collect {
                    if (_enabled.value) {
                        val gen = syncGeneration.get()
                        regularSyncJob.set(scope.launch { runSync(gen) })
                    }
                }
        }
    }

    init {
        startDebounceLoop()
        // If auto-sync was left enabled, pull/push on startup. Direct launch rather
        // than emitting into `requests`: with replay=0 the startup emit could be
        // missed if the collector hasn't subscribed yet.
        if (_enabled.value) launchRegularSync(syncGeneration.get())
    }

    override fun requestSync() {
        if (!_enabled.value) return
        requests.tryEmit(Unit)
    }

    override fun toggle() {
        val next = !_enabled.value
        _enabled.value = next
        settings.autoSyncEnabled = next
        // Enabling: sync immediately, bypassing debounce, for instant feedback.
        if (next) launchRegularSync(syncGeneration.get())
    }

    override fun syncNow() {
        launchRegularSync(syncGeneration.get())
    }

    override suspend fun prepareAccountSwitch() {
        // Invalidate any pending sync that hasn't acquired the lock yet.
        syncGeneration.incrementAndGet()
        // Cancel and await the in-flight sync (if any). Kotlin's Mutex.withLock
        // re-throws CancellationException after releasing the lock, so this
        // suspends until the sync coroutine has fully stopped and the mutex is free.
        regularSyncJob.getAndSet(null)?.cancelAndJoin()
    }

    override suspend fun resyncFromScratch() {
        val gen = syncGeneration.incrementAndGet()
        // Clear + reset run inside the sync mutex so a concurrent sync can't
        // push the old account's rows between the wipe and the pull.
        runSync(gen) {
            syncRepository.clearLocalData()
            syncRepository.resetSyncCursor()
        }
    }

    // gen: the generation this sync was scheduled for. Non-resync syncs (prepare==null)
    // abort if a resync has been requested since scheduling (gen < current generation).
    private suspend fun runSync(gen: Int, prepare: (suspend () -> Unit)? = null) {
        mutex.withLock {
            if (prepare == null && syncGeneration.get() != gen) return
            prepare?.invoke()
            _isSyncing.value = true
            try {
                syncRepository.sync().onFailure { e ->
                    _errors.tryEmit(e.message ?: "Sync failed")
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 1500L

        @Volatile private var instance: SyncManager? = null

        fun getInstance(context: Context): SyncManager =
            instance ?: synchronized(this) {
                instance ?: run {
                    val appContext = context.applicationContext
                    val db = FlashcardDatabase.getDatabase(appContext)
                    val prefs = SyncPreferences(appContext)
                    SyncManager(
                        SyncRepository(db.deckDao(), db.cardDao(), prefs),
                        prefs
                    ).also { instance = it }
                }
            }
    }
}
