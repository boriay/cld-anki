package com.catalanflashcard.data.repository

import android.util.Log
import com.catalanflashcard.BuildConfig
import com.catalanflashcard.data.network.ApiClient
import com.catalanflashcard.data.network.DailyDto
import com.catalanflashcard.data.network.FirebaseTokenProvider
import com.catalanflashcard.data.network.TokenProvider
import com.catalanflashcard.data.network.WeatherApi
import com.catalanflashcard.data.preferences.WeatherPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "WeatherRepo"

/** Coarse weather state the UI renders a background for. */
enum class WeatherCondition {
    SUNNY, CLOUDY, RAIN, SNOW;

    companion object {
        fun fromRaw(raw: String?): WeatherCondition = when (raw?.lowercase()) {
            "rain" -> RAIN
            "snow" -> SNOW
            "cloudy" -> CLOUDY
            else -> SUNNY
        }
    }
}

data class DailyForecast(
    val date: String,
    val condition: WeatherCondition,
    val tempMax: Double,
    val tempMin: Double,
    val precipitationMm: Double,
    val precipProbPct: Int,
    val cloudCoverPct: Int
)

data class WeatherState(
    val condition: WeatherCondition = WeatherCondition.SUNNY,
    val isDay: Boolean = true,
    /** Today and tomorrow, in order; empty until the first successful fetch. */
    val daily: List<DailyForecast> = emptyList()
)

/**
 * Serves weather for the background. The local [WeatherPreferences] cache caps
 * network calls at one per hour (mirroring the backend's per-location cache),
 * so [refresh] is safe to call on every app start.
 */
class WeatherRepository(
    private val prefs: WeatherPreferences,
    private val api: WeatherApi = ApiClient.weatherApi,
    private val tokenProvider: TokenProvider = FirebaseTokenProvider()
) {
    private val gson = Gson()

    /** Last known state, served instantly without any network call. */
    fun cached(): WeatherState = WeatherState(
        condition = WeatherCondition.fromRaw(prefs.condition),
        isDay = prefs.isDay,
        daily = decodeDaily(prefs.dailyJson)
    )

    /**
     * Returns fresh weather, fetching from the backend only when the cache has
     * expired. On any failure the last cached state (or the default) is returned
     * so the background is never left blank.
     */
    suspend fun refresh(): WeatherState = withContext(Dispatchers.IO) {
        if (prefs.isFresh()) return@withContext cached()
        try {
            val token = tokenProvider.idToken()
            val dto = api.current(auth = "Bearer $token")
            // `fallback` is the backend's explicit signal that it couldn't
            // resolve real weather. Don't let that clobber the last good cached
            // weather — keep showing it instead of resetting to default.
            if (dto.fallback) {
                if (BuildConfig.DEBUG) Log.d(TAG, "backend returned fallback; keeping cache")
                // If we already have good data, treat this as a completed attempt
                // so we honour the once-per-hour cap during an upstream outage
                // instead of re-hitting the backend on every launch. With no prior
                // data, leave fetchedAt untouched so the next launch keeps trying.
                if (prefs.condition != null) prefs.fetchedAt = System.currentTimeMillis()
                return@withContext cached()
            }
            val daily = dto.daily.map { it.toDomain() }
            prefs.condition = dto.condition
            prefs.isDay = dto.isDay
            prefs.dailyJson = gson.toJson(daily)
            prefs.fetchedAt = System.currentTimeMillis()
            WeatherState(WeatherCondition.fromRaw(dto.condition), dto.isDay, daily)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "weather refresh failed: ${e.message}")
            cached()
        }
    }

    private fun decodeDaily(json: String?): List<DailyForecast> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<DailyForecast>>() {}.type)
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

private fun DailyDto.toDomain() = DailyForecast(
    date = date,
    condition = WeatherCondition.fromRaw(condition),
    tempMax = tempMaxC,
    tempMin = tempMinC,
    precipitationMm = precipitationMm,
    precipProbPct = precipProbPct,
    cloudCoverPct = cloudCoverPct
)
