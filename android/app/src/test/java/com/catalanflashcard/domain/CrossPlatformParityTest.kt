package com.catalanflashcard.domain

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Cross-platform parity tests for the Android client.
 *
 * They assert the Android SM-2 ([SpacedRepetition]) and interval-formatting
 * ([IntervalFormat]) implementations against the shared golden fixtures in
 * /shared/testdata — the SAME files the web client checks (web/test/parity.test.ts).
 * Both suites passing proves the two clients schedule and format identically.
 *
 * The fixtures are generated from the web reference implementation, so this test
 * is what actually guarantees Android stays in lockstep with it.
 */
class CrossPlatformParityTest {

    private val gson = Gson()

    // Unit-test working dir is the :app module; walk up to the repo's shared dir.
    // Falls back by ascending so the test is robust to the runner's CWD.
    private fun fixture(name: String): String {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "shared/testdata/$name")
            if (f.exists()) return f.readText()
            dir = dir.parentFile
        }
        throw IllegalStateException("shared/testdata/$name not found from ${File("").absolutePath}")
    }

    private data class Sm2State(val interval: Int, val easeFactor: Double, val repetitions: Int)
    private data class Sm2Step(val quality: Int, val expected: Sm2State)
    private data class Sm2Sequence(val name: String, val start: Sm2State, val steps: List<Sm2Step>)
    private data class Sm2File(val review: List<Sm2Sequence>)

    @Test
    fun `SM-2 review sequences match the shared fixtures`() {
        val file = gson.fromJson(fixture("sm2_vectors.json"), Sm2File::class.java)
        assertTrue("fixture has sequences", file.review.isNotEmpty())

        for (seq in file.review) {
            var interval = seq.start.interval
            var ease = seq.start.easeFactor.toFloat()
            var reps = seq.start.repetitions
            seq.steps.forEachIndexed { i, step ->
                val r = SpacedRepetition.calculate(interval, ease, reps, step.quality)
                val where = "${seq.name}[$i] q=${step.quality}"
                // Integer scheduling fields must be exact — they drive the due date.
                assertEquals("$where interval", step.expected.interval, r.interval)
                assertEquals("$where repetitions", step.expected.repetitions, r.repetitions)
                // ease_factor lives on the float32 lattice, so it matches exactly
                // once the fixture double is narrowed back to Float.
                assertEquals("$where easeFactor", step.expected.easeFactor.toFloat(), r.easeFactor, 0.0f)
                interval = r.interval; ease = r.easeFactor; reps = r.repetitions
            }
        }
    }

    private data class IntervalCase(val days: Int, val expected: String)
    private data class NextReviewCase(val diffMs: Long, val expected: String)
    private data class IntervalFile(
        val units: IntervalUnits,
        val interval: List<IntervalCase>,
        val nextReview: List<NextReviewCase>
    )

    @Test
    fun `interval formatting matches the shared fixtures`() {
        val fx = gson.fromJson(fixture("interval_vectors.json"), IntervalFile::class.java)

        for (c in fx.interval) {
            assertEquals("formatInterval(${c.days})", c.expected, IntervalFormat.interval(c.days, fx.units))
        }
        for (c in fx.nextReview) {
            // Fixed now=0 so target == diffMs (matches the generator).
            assertEquals(
                "formatNextReview(${c.diffMs})",
                c.expected,
                IntervalFormat.nextReview(c.diffMs, 0L, fx.units)
            )
        }
    }
}
