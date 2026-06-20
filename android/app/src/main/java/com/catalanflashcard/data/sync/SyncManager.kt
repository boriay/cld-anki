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
 * Управляет авто-синхронизацией: общий тумблер вкл/выкл, индикатор «идёт синк»
 * и debounce-движок, коалесцирующий пачку правок в один запрос к серверу.
 *
 * Вынесено в синглтон (а не в ViewModel), потому что DeckViewModel создаётся
 * по инстансу на каждую back-stack entry, а StudyViewModel — отдельный. Состояние
 * синка должно быть общим для всех экранов.
 */
interface SyncController {
    /** Идёт ли синхронизация прямо сейчас (для индикатора в UI). */
    val isSyncing: StateFlow<Boolean>

    /** Включена ли авто-синхронизация. По умолчанию выключена. */
    val enabled: StateFlow<Boolean>

    /** Сообщения об ошибках синка для показа в UI. */
    val errors: SharedFlow<String>

    /** Запросить синк после локальной правки. No-op, если авто-синк выключен. */
    fun requestSync()

    /** Переключить авто-синк. Включение принудительно запускает синк немедленно. */
    fun toggle()
}

class SyncManager internal constructor(
    private val syncRepository: SyncRepository,
    private val settings: AutoSyncSettings,
    // Application-scope: переживает пересоздание Activity (смена локали и т.п.).
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : SyncController {

    private val _isSyncing = MutableStateFlow(false)
    override val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _enabled = MutableStateFlow(settings.autoSyncEnabled)
    override val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    // extraBufferCapacity=1 + DROP_OLDEST: пачка правок сжимается в один тик,
    // tryEmit никогда не блокирует вызывающий поток.
    private val _errors = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val requests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // Сериализует запуски синка: тик debounce, прилетевший во время активного
    // синка, дождётся его и синкнёт снова, подхватив правки сделанные по ходу.
    private val mutex = Mutex()

    @OptIn(FlowPreview::class)
    private fun startDebounceLoop() {
        scope.launch {
            requests
                .debounce(DEBOUNCE_MS)
                // Повторная проверка enabled: если синк выключили, пока тик ждал
                // окно debounce, отложенный запрос гасится, а не уезжает на сервер.
                .collect { if (_enabled.value) runSync() }
        }
    }

    init {
        startDebounceLoop()
        // Если авто-синк был оставлен включённым — подтянуть/выгрузить при старте.
        // Прямой launch, а не emit в requests: при replay=0 стартовый emit мог бы
        // потеряться, если collector ещё не успел подписаться.
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
        // Включение — немедленный синк, минуя debounce, чтобы дать мгновенный фидбэк.
        if (next) scope.launch { runSync() }
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
