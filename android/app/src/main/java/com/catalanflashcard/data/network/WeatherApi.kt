package com.catalanflashcard.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header

/** Current weather for the caller's approximate (IP-derived) location. */
interface WeatherApi {
    @GET("api/v1/weather")
    suspend fun current(
        @Header("Authorization") auth: String
    ): WeatherDto
}

data class WeatherDto(
    /** One of: sunny, cloudy, rain, snow. */
    val condition: String,
    @SerializedName("is_day") val isDay: Boolean,
    @SerializedName("temperature_c") val temperatureC: Double = 0.0,
    @SerializedName("weather_code") val weatherCode: Int = 0,
    val city: String? = null,
    @SerializedName("fetched_at") val fetchedAt: String? = null,
    /** Today and tomorrow, in order. */
    val daily: List<DailyDto> = emptyList()
)

data class DailyDto(
    val date: String,
    /** One of: sunny, cloudy, rain, snow. */
    val condition: String,
    @SerializedName("temp_max_c") val tempMaxC: Double = 0.0,
    @SerializedName("temp_min_c") val tempMinC: Double = 0.0,
    @SerializedName("precipitation_mm") val precipitationMm: Double = 0.0,
    @SerializedName("precip_prob_pct") val precipProbPct: Int = 0,
    @SerializedName("cloud_cover_pct") val cloudCoverPct: Int = 0
)
