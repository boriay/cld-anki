// SM-2 spaced repetition, ported 1:1 from the Android client
// (domain/SpacedRepetition.kt) so review scheduling is identical across clients.
//
// Quality scale:
//   1 = Again (reset)   3 = Hard   4 = Good   5 = Easy
// Quality < 3 resets repetitions and sets interval to 1 day.

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

const EASE_MIN = 1.3;
const EASE_MAX = 5.0;
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
    // Round to match the Android Int conversion (truncation toward zero) and
    // cap at INT32_MAX so the backend's INTEGER column can't overflow.
    newInterval = Math.trunc(clamp(interval * newEaseFactor, 1, INT32_MAX));
  }

  return { interval: newInterval, easeFactor: newEaseFactor, repetitions: repetitions + 1 };
}

// nextReviewTime returns an RFC3339 string `interval` days from now, matching
// the next_review_time the backend stores.
export function nextReviewTime(intervalDays: number): string {
  // Fixed 24h days (not setDate, which shifts by calendar days and drifts ±1h
  // across DST) so scheduling matches the Android interval * 24h arithmetic.
  return new Date(Date.now() + intervalDays * DAY_MS).toISOString();
}
