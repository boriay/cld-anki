package com.catalanflashcard.domain

/**
 * SM-2 spaced repetition algorithm.
 *
 * Quality scale used in this app:
 *   1 = Again  (complete failure → reset)
 *   3 = Hard   (correct but very difficult)
 *   4 = Good   (correct, normal effort)
 *   5 = Easy   (correct, effortless)
 *
 * Quality < 3 resets repetitions and sets interval to 1 day.
 */
object SpacedRepetition {

    data class ReviewResult(
        val interval: Int,
        val easeFactor: Float,
        val repetitions: Int
    )

    fun calculate(
        interval: Int,
        easeFactor: Float,
        repetitions: Int,
        quality: Int
    ): ReviewResult {
        val newEaseFactor = (easeFactor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)))
            .toFloat()
            .coerceIn(1.3f, 5.0f)

        return if (quality < 3) {
            ReviewResult(interval = 1, easeFactor = newEaseFactor, repetitions = 0)
        } else {
            val newInterval = when (repetitions) {
                0 -> 1
                1 -> 3
                else -> (interval * newEaseFactor).toInt().coerceAtLeast(1)
            }
            ReviewResult(
                interval = newInterval,
                easeFactor = newEaseFactor,
                repetitions = repetitions + 1
            )
        }
    }
}
