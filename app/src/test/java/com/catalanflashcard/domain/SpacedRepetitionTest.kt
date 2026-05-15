package com.catalanflashcard.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacedRepetitionTest {

    private val defaultEase = 2.5f

    // --- Again (quality = 1) ---

    @Test
    fun `again resets repetitions to zero`() {
        val result = SpacedRepetition.calculate(10, defaultEase, 5, quality = 1)
        assertEquals(0, result.repetitions)
    }

    @Test
    fun `again sets interval to 1`() {
        val result = SpacedRepetition.calculate(10, defaultEase, 5, quality = 1)
        assertEquals(1, result.interval)
    }

    @Test
    fun `again reduces ease factor`() {
        val result = SpacedRepetition.calculate(10, defaultEase, 5, quality = 1)
        assertTrue(result.easeFactor < defaultEase)
    }

    // --- Hard (quality = 3) is a passing grade ---

    @Test
    fun `hard on first repetition gives interval 1`() {
        val result = SpacedRepetition.calculate(1, defaultEase, 0, quality = 3)
        assertEquals(1, result.interval)
        assertEquals(1, result.repetitions)
    }

    @Test
    fun `hard on second repetition gives interval 3`() {
        val result = SpacedRepetition.calculate(1, defaultEase, 1, quality = 3)
        assertEquals(3, result.interval)
        assertEquals(2, result.repetitions)
    }

    @Test
    fun `hard increments repetitions`() {
        val result = SpacedRepetition.calculate(10, defaultEase, 3, quality = 3)
        assertEquals(4, result.repetitions)
    }

    // --- Good (quality = 4) ---

    @Test
    fun `good on first repetition gives interval 1`() {
        val result = SpacedRepetition.calculate(1, defaultEase, 0, quality = 4)
        assertEquals(1, result.interval)
        assertEquals(1, result.repetitions)
    }

    @Test
    fun `good on second repetition gives interval 3`() {
        val result = SpacedRepetition.calculate(1, defaultEase, 1, quality = 4)
        assertEquals(3, result.interval)
        assertEquals(2, result.repetitions)
    }

    @Test
    fun `good on subsequent repetition grows interval`() {
        val result = SpacedRepetition.calculate(10, defaultEase, 2, quality = 4)
        assertTrue(result.interval > 10)
    }

    @Test
    fun `good keeps ease factor roughly stable`() {
        val result = SpacedRepetition.calculate(10, defaultEase, 2, quality = 4)
        assertEquals(defaultEase, result.easeFactor, 0.01f)
    }

    // --- Easy (quality = 5) ---

    @Test
    fun `easy increases ease factor`() {
        val result = SpacedRepetition.calculate(10, defaultEase, 2, quality = 5)
        assertTrue(result.easeFactor > defaultEase)
    }

    @Test
    fun `easy gives longer interval than good`() {
        val goodResult = SpacedRepetition.calculate(10, defaultEase, 2, quality = 4)
        val easyResult = SpacedRepetition.calculate(10, defaultEase, 2, quality = 5)
        assertTrue(easyResult.interval >= goodResult.interval)
    }

    // --- Ease factor bounds ---

    @Test
    fun `ease factor never drops below 1_3`() {
        var ease = defaultEase
        repeat(20) {
            val result = SpacedRepetition.calculate(1, ease, 0, quality = 1)
            ease = result.easeFactor
        }
        assertTrue(ease >= 1.3f)
    }

    @Test
    fun `ease factor never exceeds 5_0`() {
        var ease = defaultEase
        repeat(20) {
            val result = SpacedRepetition.calculate(1, ease, 0, quality = 5)
            ease = result.easeFactor
        }
        assertTrue(ease <= 5.0f)
    }

    // --- Interval always positive ---

    @Test
    fun `interval is always at least 1`() {
        listOf(1, 3, 4, 5).forEach { quality ->
            val result = SpacedRepetition.calculate(0, 1.3f, 2, quality)
            assertTrue("interval must be >= 1 for quality $quality", result.interval >= 1)
        }
    }
}
