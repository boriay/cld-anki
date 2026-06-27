// Regenerates the shared cross-platform golden fixtures in /shared/testdata from
// the web reference implementation. Run: npm run test:gen
//
// SM-2 vectors are SEQUENCES applied to a fresh card so every input ease_factor
// is itself a prior algorithm output (a float32-representable value). That keeps
// the web (double) and Android (Float) computations on the same lattice, so the
// expected results match bit-for-bit on both platforms — no tolerance needed.
//
// After regenerating, run the parity suites on BOTH clients (npm test here and
// the Android CrossPlatformParityTest) to confirm they still agree.

import { writeFileSync } from "node:fs";
import { calculateReview, Quality } from "../src/domain/sm2.ts";
import { formatInterval, formatNextReview, type IntervalUnits } from "../src/domain/interval.ts";

const OUT = new URL("../../shared/testdata/", import.meta.url);

const Q = Quality;
const sequences: Array<{ name: string; grades: Quality[] }> = [
  { name: "all_good", grades: [Q.Good, Q.Good, Q.Good, Q.Good, Q.Good, Q.Good] },
  { name: "good_then_again", grades: [Q.Good, Q.Good, Q.Good, Q.Again, Q.Good, Q.Good] },
  // Drive ease to the floor with Hard, then Good multiplies on the floored ease
  // (exercises the trunc boundary that previously diverged web vs Android).
  { name: "hard_to_floor", grades: [Q.Hard, Q.Hard, Q.Hard, Q.Hard, Q.Hard, Q.Hard, Q.Hard, Q.Hard, Q.Hard, Q.Hard, Q.Good, Q.Good] },
  { name: "easy_climb", grades: [Q.Easy, Q.Easy, Q.Easy, Q.Easy, Q.Easy, Q.Easy] },
  { name: "mixed", grades: [Q.Good, Q.Hard, Q.Easy, Q.Again, Q.Good, Q.Easy, Q.Hard] },
  // Many Good in a row pushes interval into the Int32 overflow cap.
  { name: "overflow", grades: Array(40).fill(Q.Good) },
];

const review = sequences.map(({ name, grades }) => {
  let interval = 1, easeFactor = 2.5, repetitions = 0;
  const steps = grades.map((quality) => {
    const r = calculateReview(interval, easeFactor, repetitions, quality);
    interval = r.interval; easeFactor = r.easeFactor; repetitions = r.repetitions;
    return { quality, expected: { interval, easeFactor, repetitions } };
  });
  return { name, start: { interval: 1, easeFactor: 2.5, repetitions: 0 }, steps };
});

writeFileSync(new URL("sm2_vectors.json", OUT), JSON.stringify({ review }, null, 2) + "\n");

// --- Interval formatting vectors (platform-deterministic strings) ---
const units: IntervalUnits = { minute: "min", hour: "h", day: "d", month: "mo", year: "y", now: "now" };

const intervalDays = [0, 1, 7, 29, 30, 45, 75, 364, 365, 547, 730, 2000];
const interval = intervalDays.map((days) => ({ days, expected: formatInterval(days, units) }));

const MIN = 60_000, HOUR = 3_600_000, DAY = 86_400_000;
const diffs = [-1000, 0, 30 * 1000, 30 * MIN, 90 * MIN, 5 * HOUR, 25 * HOUR, 3 * DAY, 40 * DAY, 365 * DAY, 800 * DAY];
// Fixed now=0 so target == diffMs; both clients pass an explicit "now".
const nextReview = diffs.map((diffMs) => ({ diffMs, expected: formatNextReview(diffMs, units, 0) }));

writeFileSync(new URL("interval_vectors.json", OUT), JSON.stringify({ units, interval, nextReview }, null, 2) + "\n");

const stepCount = review.reduce((n, s) => n + s.steps.length, 0);
console.log(`generated ${review.length} sequences / ${stepCount} steps + ${interval.length} interval + ${nextReview.length} nextReview`);
