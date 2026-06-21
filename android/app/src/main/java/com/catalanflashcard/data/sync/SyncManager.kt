package com.catalanflashcard.data.sync

import android.content.Context
import com.catalanflashcard.data.database.FlashcardDatabase
import com.catalanflashcard.data.preferences.AutoSyncSettings
import com.catalanflashcard.data.preferences.SyncPreferences
import com.catalanflashcard.data.repository.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
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
     * Reset the sync cursor and force a full re-sync. Used after switching to a
     * different account so the new account's data is pulled from scratch.
     */
    fun resyncFromScratch()
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

    @OptIn(FlowPreview::class)
    private fun startDebounceLoop() {
        scope.launch {
            requests
                .debounce(DEBOUNCE_MS)
                // Re-check enabled: if auto-sync was turned off while the tick was
                // waiting out the debounce window, drop the pending request instead
                // of pushing it to the server.
                .collect { if (_enabled.value) runSync() }
        }
    }

    init {
        startDebounceLoop()
        // If auto-sync was left enabled, pull/push on startup. Direct launch rather
        // than emitting into `requests`: with replay=0 the startup emit could be
        // missed if the collector hasn't subscribed yet.
        if (_enabled.value) scope.launch { runSync() }
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
        if (next) scope.launch { runSync() }
    }

    override fun syncNow() {
        scope.launch { runSync() }
    }

    override fun resyncFromScratch() {
        scope.launch {
            syncRepository.resetSyncCursor()
            runSync()
        }
    }

    private suspend fun runSync() {
        mutex.withLock {
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
