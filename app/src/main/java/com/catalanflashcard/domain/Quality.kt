package com.catalanflashcard.domain

/**
 * Review grade chosen by the user, mapped to the SM-2 quality scale.
 * See [SpacedRepetition] for how each value affects scheduling.
 */
enum class Quality(val value: Int) {
    AGAIN(1),
    HARD(3),
    GOOD(4),
    EASY(5)
}
