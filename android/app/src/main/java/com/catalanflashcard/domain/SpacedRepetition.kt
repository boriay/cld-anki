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

    // Hard/Easy multipliers (Anki defaults). On the float32 lattice so
    // multiplications stay bit-identical with the web client.
    private const val EASY_BONUS  = 1.3f
    private const val HARD_FACTOR = 1.2f

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
                    // Good baseline — other grades are anchored to this so ordering is guaranteed.
                    val goodInterval = (interval.toDouble() * newEaseFactor)
                        .coerceIn(1.0, Int.MAX_VALUE.toDouble()).toInt()
                    when (quality) {
                        5 -> { // Easy: always strictly above Good
                            val raw = (interval.toDouble() * newEaseFactor * EASY_BONUS)
                                .coerceIn(1.0, Int.MAX_VALUE.toDouble()).toInt()
                            maxOf(goodInterval + 1, raw)
                        }
                        3 -> { // Hard: always strictly below Good
                            val raw = (interval.toDouble() * newEaseFactor * HARD_FACTOR)
                                .coerceIn(1.0, Int.MAX_VALUE.toDouble()).toInt()
                            minOf(maxOf(1, goodInterval - 1), raw)
                        }
                        else -> goodInterval
                    }
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
