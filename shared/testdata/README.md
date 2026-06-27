# Shared cross-platform golden fixtures

These JSON fixtures are the single source of truth for verifying that the web and
Android clients schedule and format reviews **identically**. Both clients run a
parity suite against the same files:

- Web: [`web/test/parity.test.ts`](../../web/test/parity.test.ts) — `npm test`
- Android: [`CrossPlatformParityTest.kt`](../../android/app/src/test/java/com/catalanflashcard/domain/CrossPlatformParityTest.kt) — `./gradlew :app:testDebugUnitTest`

Passing on both sides proves the SM-2 algorithm
([`sm2.ts`](../../web/src/domain/sm2.ts) ↔ [`SpacedRepetition.kt`](../../android/app/src/main/java/com/catalanflashcard/domain/SpacedRepetition.kt))
and interval formatting
([`interval.ts`](../../web/src/domain/interval.ts) ↔ [`IntervalFormat.kt`](../../android/app/src/main/java/com/catalanflashcard/domain/IntervalFormat.kt))
agree byte-for-byte.

## Files

- `sm2_vectors.json` — SM-2 review **sequences**. Each starts from a fresh card
  (`interval=1, ease=2.5, reps=0`) and applies a list of grades, recording the
  result after every step. Sequences (not isolated inputs) guarantee every input
  `ease_factor` is a real prior output, i.e. float32-representable, so web
  (double) and Android (Float) land on identical values — `easeFactor` is
  compared exactly, not with a tolerance.
- `interval_vectors.json` — `formatInterval(days)` and `formatNextReview(diffMs)`
  cases with a fixed English unit set. `nextReview` uses `now=0`, so the card's
  due timestamp is exactly `diffMs`.

## Regenerating

The web reference implementation is the generator. After changing the SM-2 or
formatting logic, regenerate and re-run **both** suites:

```bash
cd web
npm run test:gen   # rewrites ../shared/testdata/*.json from sm2.ts / interval.ts
npm test           # web parity
cd ../android && ./gradlew :app:testDebugUnitTest   # android parity
```

> Note on the ease floor: clients compute `ease_factor` on a float32 lattice, so
> the real minimum is `float32(1.3) = 1.2999999523…`, not the double `1.3`. The
> web clamp (`Math.fround`) and the backend bounds
> ([`backend/internal/handler/sm2.go`](../../backend/internal/handler/sm2.go))
> are aligned to that value so a floored card round-trips through sync untouched.
