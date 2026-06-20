package com.catalanflashcard.data.preferences

import android.content.Context

// Узкий контракт того, что нужно SyncManager от настроек: только флаг авто-синка.
// Позволяет тестировать SyncManager без Android Context (фейк вместо SharedPreferences).
interface AutoSyncSettings {
    var autoSyncEnabled: Boolean
}

class SyncPreferences(context: Context) : AutoSyncSettings {
    private val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    // Server-issued timestamp of the last sync. Used as the `since` cursor when
    // asking the server what changed — must stay on the server's clock.
    var lastSyncedAt: Long
        get() = prefs.getLong(KEY_LAST_SYNCED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNCED_AT, value).apply()

    // Local (client-clock) timestamp of the last successful push. Used to select
    // which local rows to upload. Kept separate from lastSyncedAt so client/server
    // clock skew can't cause local edits to be skipped on upload.
    var lastPushedAt: Long
        get() = prefs.getLong(KEY_LAST_PUSHED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_PUSHED_AT, value).apply()

    // Включена ли авто-синхронизация. По умолчанию выключена, чтобы приложение
    // оставалось полностью офлайн, пока пользователь сам не включит синк.
    override var autoSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, value).apply()

    companion object {
        private const val KEY_LAST_SYNCED_AT = "last_synced_at"
        private const val KEY_LAST_PUSHED_AT = "last_pushed_at"
        private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
    }
}
