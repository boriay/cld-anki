// Human-readable formatting for review intervals, shared by the Study grade
// buttons (preview of where each answer moves the card) and the deck list
// (when each card is next due). Uses short, non-inflecting unit suffixes
// (Anki-style: 3d, 2mo, 1.5y) to sidestep per-language plural rules.

export interface IntervalUnits {
  minute: string;
  hour: string;
  day: string;
  month: string;
  year: string;
  now: string;
}

const DAY_MS = 86_400_000;

// Buckets a whole-day count into d / mo / y. Months ≈30d, years ≈365d.
function formatDays(days: number, u: IntervalUnits): string {
  if (days < 30) return `${days}${u.day}`;
  if (days < 365) return `${Math.round(days / 30)}${u.month}`;
  const years = days / 365;
  return `${years % 1 === 0 ? years : years.toFixed(1)}${u.year}`;
}

// Preview for a grade button: `intervalDays` is always >= 1 (SM-2 minimum).
export function formatInterval(intervalDays: number, u: IntervalUnits): string {
  return formatDays(Math.max(1, Math.round(intervalDays)), u);
}

// When a card is next due, relative to `now`. Sub-day gaps fall back to
// hours/minutes; past-due cards read as "now".
export function formatNextReview(
  targetMs: number,
  u: IntervalUnits,
  now: number = Date.now(),
): string {
  const diff = targetMs - now;
  if (diff <= 0) return u.now;
  const minutes = diff / 60_000;
  if (minutes < 60) return `${Math.max(1, Math.round(minutes))}${u.minute}`;
  const hours = minutes / 60;
  if (hours < 24) return `${Math.round(hours)}${u.hour}`;
  return formatDays(Math.round(diff / DAY_MS), u);
}
