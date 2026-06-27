// SM-2 spaced repetition, ported 1:1 from the Android client
// (domain/SpacedRepetition.kt) so review scheduling is identical across clients.
//
// Quality scale:
//   1 = Again (reset)   3 = Hard   4 = Good   5 = Easy
// Quality < 3 resets repetitions and sets interval to 1 day.
//
// Extensions (Anki-style) to guarantee Hard < Good < Easy for every card:
//   HARD_FACTOR 1.2× — Hard interval = interval * ease_hard * 1.2, then
//     capped at (Good − 1) so Hard is always strictly below Good.
//   EASY_BONUS  1.3× — Easy interval = interval * ease_easy * 1.3, always
//     strictly above Good.

export enum Quality {
  Again = 1,
  Hard = 3,
  Good = 4,
  Easy = 5,
}

export interface ReviewResult {
  interval: number;
  easeFactor: number;
  repetitions: number;
}

// Clamp bounds live on the float32 lattice (Math.fround) because the Android
// client stores ease_factor as a 32-bit Float. Using the plain double 1.3 here
// would clamp web ease slightly higher than Android's 1.3f floor, and on
// low-ease cards that one-ULP gap flips trunc(interval * ease) to a different
// integer interval — i.e. web and Android would schedule the same card to
// different days. fround keeps both clients on identical representable values.
const EASE_MIN = Math.fround(1.3); // 1.2999999523162842
const EASE_MAX = Math.fround(5.0); // 5.0
// Hard/Easy multipliers on the float32 lattice so multiplications stay
// bit-identical with Android (which stores easeFactor as Float).
const EASY_BONUS  = Math.fround(1.3);
const HARD_FACTOR = Math.fround(1.2);
// Match the Android Int.MAX_VALUE cap: the backend stores interval as a 32-bit
// INTEGER, so a long review streak must not overflow it.
const INT32_MAX = 2_147_483_647;
const DAY_MS = 86_400_000;

function clamp(v: number, lo: number, hi: number): number {
  return Math.min(Math.max(v, lo), hi);
}

export function calculateReview(
  interval: number,
  easeFactor: number,
  repetitions: number,
  quality: Quality,
): ReviewResult {
  const delta = 5 - quality; // how far below ideal (0 = Easy)
  // Math.fround matches Android's .toFloat() — both compute in double precision
  // then truncate to Float32, so easeFactor stays bit-identical across clients.
  const newEaseFactor = clamp(
    Math.fround(easeFactor + 0.1 - delta * (0.08 + delta * 0.02)),
    EASE_MIN,
    EASE_MAX,
  );

  if (quality < Quality.Hard) {
    return { interval: 1, easeFactor: newEaseFactor, repetitions: 0 };
  }

  let newInterval: number;
  if (repetitions === 0) {
    newInterval = 1;
  } else if (repetitions === 1) {
    newInterval = 3;
  } else {
    // Good baseline — other grades are anchored to this so ordering is guaranteed.
    const goodInterval = Math.trunc(clamp(interval * newEaseFactor, 1, INT32_MAX));
    if (quality === Quality.Easy) {
      // Always strictly above Good.
      const raw = Math.trunc(clamp(interval * newEaseFactor * EASY_BONUS, 1, INT32_MAX));
      newInterval = Math.max(goodInterval + 1, raw);
    } else if (quality === Quality.Hard) {
      // Always strictly below Good.
      const raw = Math.trunc(clamp(interval * newEaseFactor * HARD_FACTOR, 1, INT32_MAX));
      newInterval = Math.min(Math.max(1, goodInterval - 1), raw);
    } else {
      newInterval = goodInterval;
    }
  }

  return { interval: newInterval, easeFactor: newEaseFactor, repetitions: repetitions + 1 };
}

// JS Date supports ±8_640_000_000_000_000 ms from epoch; above that toISOString
// throws RangeError. INT32_MAX days (≈5.8M years) vastly exceeds that limit, so
// clamp to the last representable date (~year 275 760) rather than crashing.
const MAX_DATE_DAYS = Math.floor(8_640_000_000_000_000 / DAY_MS); // 100_000_000

// nextReviewTime returns an RFC3339 string `interval` days from now, matching
// the next_review_time the backend stores.
export function nextReviewTime(intervalDays: number): string {
  // Fixed 24h days (not setDate, which shifts by calendar days and drifts ±1h
  // across DST) so scheduling matches the Android interval * 24h arithmetic.
  return new Date(Date.now() + Math.min(intervalDays, MAX_DATE_DAYS) * DAY_MS).toISOString();
}
