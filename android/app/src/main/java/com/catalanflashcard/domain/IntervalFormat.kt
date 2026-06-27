package com.catalanflashcard.domain

import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Localized unit suffixes for [IntervalFormat]. Short and non-inflecting
 * (Anki-style: 3d, 2mo, 1.5y) to avoid per-language plural rules.
 */
data class IntervalUnits(
    val minute: String,
    val hour: String,
    val day: String,
    val month: String,
    val year: String,
    val now: String,
)

/**
 * Human-readable review interval formatting, mirrored 1:1 with the web client
 * (web/src/domain/interval.ts) so both surfaces read identically.
 */
object IntervalFormat {
    private const val DAY_MS = 86_400_000.0

    // Buckets a whole-day count into d / mo / y. Months ≈30d, years ≈365d.
    private fun formatDays(days: Int, u: IntervalUnits): String = when {
        days < 30 -> "$days${u.day}"
        days < 365 -> "${(days / 30.0).roundToInt()}${u.month}"
        else -> {
            val years = days / 365.0
            val text = if (years % 1.0 == 0.0) years.toInt().toString()
            else String.format(Locale.US, "%.1f", years)
            "$text${u.year}"
        }
    }

    /** Preview for a grade button: [intervalDays] is always >= 1 (SM-2 minimum). */
    fun interval(intervalDays: Int, u: IntervalUnits): String =
        formatDays(max(1, intervalDays), u)

    /** When a card is next due, relative to [nowMs]. Past-due reads as "now". */
    fun nextReview(targetMs: Long, nowMs: Long, u: IntervalUnits): String {
        val diff = targetMs - nowMs
        if (diff <= 0L) return u.now
        val minutes = diff / 60_000.0
        if (minutes < 60) return "${max(1, minutes.roundToInt())}${u.minute}"
        val hours = minutes / 60
        if (hours < 24) return "${hours.roundToInt()}${u.hour}"
        return formatDays((diff / DAY_MS).roundToInt(), u)
    }
}
