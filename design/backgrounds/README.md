# Weather meadow backgrounds

Decorative app backgrounds: a park meadow rendered per weather/time state. The
client picks an asset from the weather API response (`condition` + `is_day`).

## States (8)

`bg_meadow_<time>_<condition>` where `time ∈ {day, night}` and
`condition ∈ {sunny, cloudy, rain, snow}`. (`sunny` at night = clear, moon + stars.)

## Single source of truth

`generate.py` describes each scene once and emits both formats from the same
geometry, so platforms never drift:

- **SVG** (here, `*.svg`) — universal. Render directly on iOS (SwiftUI / asset
  catalog) and web, or rasterise to PNG/WebP.
- **Android VectorDrawable** — written to
  `android/app/src/main/res/drawable/bg_meadow_*.xml`.

Regenerate after editing scenes:

```bash
python3 design/backgrounds/generate.py
```

## Consuming on iOS

Drop the SVGs into the asset catalog (Xcode supports SVG; it preserves vectors),
then map a weather state to the asset name exactly as Android does:

```
sunny  -> bg_meadow_<day|night>_sunny
cloudy -> bg_meadow_<day|night>_cloudy
rain   -> bg_meadow_<day|night>_rain
snow   -> bg_meadow_<day|night>_snow
```

The backend contract (`GET /api/v1/weather`, cached per-location ≤ 1×/hour) is
platform-agnostic and shared.

## Swapping for hand-drawn art

Replace any `*.svg` (or the platform asset) with richer artwork of the same name
— no code changes. Keep the 8-state naming so the mapping stays valid.
