package com.catalanflashcard.ui

import java.util.Locale

// Single source of truth for the languages the app supports. The UI strings,
// the seeded decks, and the deck-list filter all key off these tags.
const val DEFAULT_LANGUAGE = "en"

val SUPPORTED_LANGUAGES = listOf("en", "es", "ru")

/**
 * Maps an arbitrary locale to one of the supported language tags, falling back
 * to [DEFAULT_LANGUAGE] (English) when the locale isn't one we ship. Used to pick
 * which language's seeded decks to show for the current UI locale.
 */
fun resolveAppLanguage(locale: Locale): String {
    val tag = locale.language.lowercase(Locale.ROOT)
    return if (tag in SUPPORTED_LANGUAGES) tag else DEFAULT_LANGUAGE
}
