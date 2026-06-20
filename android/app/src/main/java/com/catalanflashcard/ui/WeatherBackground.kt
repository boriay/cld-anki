package com.catalanflashcard.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.catalanflashcard.R
import com.catalanflashcard.data.repository.WeatherCondition
import com.catalanflashcard.data.repository.WeatherState

/** Maps a weather state to its meadow background drawable. */
@DrawableRes
fun backgroundFor(state: WeatherState): Int = when (state.condition) {
    WeatherCondition.SUNNY -> if (state.isDay) R.drawable.bg_meadow_day_sunny else R.drawable.bg_meadow_night_sunny
    WeatherCondition.CLOUDY -> if (state.isDay) R.drawable.bg_meadow_day_cloudy else R.drawable.bg_meadow_night_cloudy
    WeatherCondition.RAIN -> if (state.isDay) R.drawable.bg_meadow_day_rain else R.drawable.bg_meadow_night_rain
    WeatherCondition.SNOW -> if (state.isDay) R.drawable.bg_meadow_day_snow else R.drawable.bg_meadow_night_snow
}

/**
 * Full-screen meadow background that cross-fades when the weather state changes.
 * Rendered as the bottom layer beneath all app content.
 */
@Composable
fun WeatherBackground(state: WeatherState, modifier: Modifier = Modifier) {
    Crossfade(
        targetState = backgroundFor(state),
        animationSpec = tween(durationMillis = 800),
        label = "weather-background"
    ) { resId ->
        Image(
            painter = painterResource(resId),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize()
        )
    }
}
