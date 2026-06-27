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

    // Easy bonus: separates Easy from Good even on short intervals where the
    // ease-factor difference alone doesn't add a full day. Matches Anki default.
    // On the float32 lattice so the multiplication stays bit-identical with web.
    private const val EASY_BONUS = 1.3f

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
        val delta = 5 - quality   // насколько ответ хуже идеального (0 = Easy)
        val newEaseFactor = (easeFactor + 0.1 - delta * (0.08 + delta * 0.02))
            .toFloat()
            .coerceIn(1.3f, 5.0f)

        return if (quality < 3) {
            ReviewResult(interval = 1, easeFactor = newEaseFactor, repetitions = 0)
        } else {
            val newInterval = when (repetitions) {
                0 -> 1
                1 -> 3
                else -> {
                    // Compute in Double and clamp to avoid Int overflow on long-learned cards.
                    val bonus = if (quality == 5) EASY_BONUS else 1f
                    val raw = interval.toDouble() * newEaseFactor * bonus
                    raw.coerceIn(1.0, Int.MAX_VALUE.toDouble()).toInt()
                }
            }
            ReviewResult(
                interval = newInterval,
                easeFactor = newEaseFactor,
                repetitions = repetitions + 1
            )
        }
    }
}
