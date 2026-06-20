package com.catalanflashcard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.catalanflashcard.R
import com.catalanflashcard.data.repository.DailyForecast
import com.catalanflashcard.data.repository.WeatherCondition
import com.catalanflashcard.data.repository.WeatherState
import java.time.LocalDate
import kotlin.math.roundToInt

private fun WeatherCondition.emoji(): String = when (this) {
    WeatherCondition.SUNNY -> "☀️"
    WeatherCondition.CLOUDY -> "☁️"
    WeatherCondition.RAIN -> "🌧️"
    WeatherCondition.SNOW -> "❄️"
}

/**
 * Tiny translucent today/tomorrow summary: condition, temperature, cloud cover
 * and precipitation chance. Renders nothing until a forecast has been fetched.
 */
@Composable
fun WeatherStrip(state: WeatherState, modifier: Modifier = Modifier) {
    // Label by actual date, not array position: if the cache is stale across
    // local midnight, daily[0] could be yesterday — matching on date avoids
    // mislabeling it "Today". Missing days are simply skipped.
    val byDate = state.daily.associateBy { it.date }
    val today = LocalDate.now()
    val cells = listOfNotNull(
        byDate[today.toString()]?.let { R.string.weather_today to it },
        byDate[today.plusDays(1).toString()]?.let { R.string.weather_tomorrow to it }
    )
    if (cells.isEmpty()) return

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            cells.forEachIndexed { index, (labelRes, day) ->
                if (index > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                        modifier = Modifier.width(1.dp).height(34.dp)
                    ) {}
                }
                DayCell(label = stringResource(labelRes), day = day)
            }
        }
    }
}

@Composable
private fun DayCell(label: String, day: DailyForecast) {
    val cloudLabel = stringResource(R.string.weather_cloudiness)
    val precipLabel = stringResource(R.string.weather_precipitation)
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${day.condition.emoji()}  ${day.tempMax.roundToInt()}°/${day.tempMin.roundToInt()}°",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "☁ ${day.cloudCoverPct}%   💧 ${day.precipProbPct}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics {
                contentDescription = "$cloudLabel ${day.cloudCoverPct}%, $precipLabel ${day.precipProbPct}%"
            }
        )
    }
}
