package com.catalanflashcard.data.preferences

import android.content.Context

/**
 * Caches the last fetched weather so the app renders a background instantly on
 * launch and never hits the backend more than once per [CACHE_TTL_MILLIS].
 */
class WeatherPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)

    var condition: String?
        get() = prefs.getString(KEY_CONDITION, null)
        set(value) = prefs.edit().putString(KEY_CONDITION, value).apply()

    var isDay: Boolean
        get() = prefs.getBoolean(KEY_IS_DAY, true)
        set(value) = prefs.edit().putBoolean(KEY_IS_DAY, value).apply()

    /** Epoch millis of the last successful fetch; 0 if never fetched. */
    var fetchedAt: Long
        get() = prefs.getLong(KEY_FETCHED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_FETCHED_AT, value).apply()

    /** JSON-encoded today/tomorrow forecast; null until the first fetch. */
    var dailyJson: String?
        get() = prefs.getString(KEY_DAILY_JSON, null)
        set(value) = prefs.edit().putString(KEY_DAILY_JSON, value).apply()

    /** True when the cache is still within its TTL and can be served as-is. */
    fun isFresh(now: Long = System.currentTimeMillis()): Boolean =
        condition != null && now - fetchedAt < CACHE_TTL_MILLIS

    companion object {
        const val CACHE_TTL_MILLIS = 60 * 60 * 1000L // 1 hour
        private const val KEY_CONDITION = "condition"
        private const val KEY_IS_DAY = "is_day"
        private const val KEY_FETCHED_AT = "fetched_at"
        private const val KEY_DAILY_JSON = "daily_json"
    }
}
