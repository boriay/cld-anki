package com.catalanflashcard.data.sync

import android.content.Context
import com.catalanflashcard.data.database.FlashcardDatabase
import com.catalanflashcard.data.database.LocalSeeder
import com.catalanflashcard.data.preferences.AutoSyncSettings
import com.catalanflashcard.data.preferences.SyncPreferences
import com.catalanflashcard.data.repository.SyncRepository
import java.util.concurrent.atomic.AtomicBoolean
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
     * Begin an account switch. Cancels any in-flight regular sync, invalidates
     * pending ones, and blocks all regular syncs from starting until
     * [applyAccountSwitch] or [abortAccountSwitch] is called. MUST run BEFORE the
     * Firebase sign-in/out so no sync can acquire the new UID's token while still
     * holding the old account's data in the local DB.
     */
    suspend fun prepareAccountSwitch()

    /**
     * Finish an account switch after the Firebase UID has changed. Optionally
     * wipes the local store (when the UID actually changed) and re-pulls, then
     * re-seeds the default decks if the result is empty (used on sign-out to a
     * fresh anonymous account). Suspends until done; always re-enables syncing.
     */
    suspend fun applyAccountSwitch(wipeLocal: Boolean, reseedIfEmpty: Boolean)

    /** Abort an account switch (e.g. auth failed): re-enable syncing and resync. */
    fun abortAccountSwitch()
}

class SyncManager internal constructor(
    private val syncRepository: SyncRepository,
    private val settings: AutoSyncSettings,
    private val localSeeder: LocalSeeder,
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

    // Bumped on every prepareAccountSwitch(): a regular sync captures the
    // generation before the lock; if it changed by the time the lock is acquired,
    // the sync is stale and is dropped.
    private val syncGeneration = AtomicInteger(0)

    // True between prepareAccountSwitch() and applyAccountSwitch()/abortAccountSwitch().
    // While set, every regular sync aborts at the top of the lock — this is what
    // stops a debounce tick that fires mid-switch (and would read the *latest*
    // generation, slipping past the generation guard) from pushing the old
    // account's local rows under the new UID.
    private val accountSwitchInProgress = AtomicBoolean(false)

    // Tracks the Job of the most recently launched regular sync so
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
                    if (_enabled.value) launchRegularSync(syncGeneration.get())
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
        // Block new regular syncs from running for the whole switch...
        accountSwitchInProgress.set(true)
        // ...and invalidate any already-queued sync via the generation bump.
        syncGeneration.incrementAndGet()
        // Speed up the common case by cancelling the most recent regular sync.
        regularSyncJob.getAndSet(null)?.cancelAndJoin()
        // Guarantee no sync is *inside* the lock: acquiring (then releasing) the
        // mutex blocks until any in-flight sync has fully drained. After this
        // returns, no regular sync holds the lock and none can start (flag set),
        // so the upcoming Firebase UID change can't be observed mid-sync.
        mutex.withLock { }
    }

    override suspend fun applyAccountSwitch(wipeLocal: Boolean, reseedIfEmpty: Boolean) {
        try {
            // prepare runs under the lock, before the pull: wipe the old account's
            // rows (atomic, FK-safe) and reset the cursor so the pull is full.
            runSync(syncGeneration.incrementAndGet()) {
                if (wipeLocal) {
                    localSeeder.wipe()
                    syncRepository.resetSyncCursor()
                }
            }
            // After the pull, restore the default decks for a brand-new empty store
            // (e.g. the fresh anonymous account after sign-out) so it isn't blank.
            // Still under the lock + flag, so no concurrent sync interferes.
            if (reseedIfEmpty) {
                mutex.withLock {
                    if (localSeeder.isEmpty()) localSeeder.seedDefaults()
                }
            }
        } finally {
            accountSwitchInProgress.set(false)
        }
    }

    override fun abortAccountSwitch() {
        accountSwitchInProgress.set(false)
        // The UID never changed (auth failed), so the local data is still the
        // current account's — a normal sync re-converges it.
        launchRegularSync(syncGeneration.get())
    }

    // gen: the generation this sync was scheduled for. Regular syncs (prepare==null)
    // abort if an account switch is in progress or a newer switch was requested
    // since scheduling. The switch's own prepare (prepare!=null) always runs.
    private suspend fun runSync(gen: Int, prepare: (suspend () -> Unit)? = null) {
        mutex.withLock {
            if (prepare == null && (accountSwitchInProgress.get() || syncGeneration.get() != gen)) return
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
                        prefs,
                        LocalSeeder(db)
                    ).also { instance = it }
                }
            }
    }
}
