// Cross-platform parity tests for the web client.
//
// These assert the web SM-2 and interval-formatting implementations against the
// shared golden fixtures in /shared/testdata. The SAME fixtures are checked by
// the Android client (CrossPlatformParityTest.kt), so a passing suite on both
// sides proves the two clients schedule and format identically. The fixtures
// are generated from this (reference) implementation, so this side also guards
// against future regressions.
//
// Run: npm test   (node --experimental-transform-types --test)

import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { calculateReview, type Quality } from "../src/domain/sm2.ts";
import { formatInterval, formatNextReview, type IntervalUnits } from "../src/domain/interval.ts";

function load<T>(name: string): T {
  const url = new URL(`../../shared/testdata/${name}`, import.meta.url);
  return JSON.parse(readFileSync(url, "utf8")) as T;
}

interface Sm2Step {
  quality: number;
  expected: { interval: number; easeFactor: number; repetitions: number };
}
interface Sm2Sequence {
  name: string;
  start: { interval: number; easeFactor: number; repetitions: number };
  steps: Sm2Step[];
}

test("SM-2 review sequences match the shared fixtures", () => {
  const { review } = load<{ review: Sm2Sequence[] }>("sm2_vectors.json");
  assert.ok(review.length > 0, "fixture has sequences");

  for (const seq of review) {
    let { interval, easeFactor, repetitions } = seq.start;
    seq.steps.forEach((step, i) => {
      const r = calculateReview(interval, easeFactor, repetitions, step.quality as Quality);
      const where = `${seq.name}[${i}] q=${step.quality}`;
      // Integer scheduling fields must be exact — they drive the due date.
      assert.equal(r.interval, step.expected.interval, `${where} interval`);
      assert.equal(r.repetitions, step.expected.repetitions, `${where} repetitions`);
      // ease_factor is on the float32 lattice, so exact equality holds too.
      assert.equal(r.easeFactor, step.expected.easeFactor, `${where} easeFactor`);
      interval = r.interval; easeFactor = r.easeFactor; repetitions = r.repetitions;
    });
  }
});

interface IntervalFixture {
  units: IntervalUnits;
  interval: Array<{ days: number; expected: string }>;
  nextReview: Array<{ diffMs: number; expected: string }>;
}

test("interval formatting matches the shared fixtures", () => {
  const fx = load<IntervalFixture>("interval_vectors.json");

  for (const c of fx.interval) {
    assert.equal(formatInterval(c.days, fx.units), c.expected, `formatInterval(${c.days})`);
  }
  for (const c of fx.nextReview) {
    // Fixed now=0 so target == diffMs (matches the generator).
    assert.equal(formatNextReview(c.diffMs, fx.units, 0), c.expected, `formatNextReview(${c.diffMs})`);
  }
});
