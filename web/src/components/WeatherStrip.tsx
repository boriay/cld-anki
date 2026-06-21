import { Fragment } from "react";
import type { DailyForecast, WeatherCondition } from "../api/types";
import { useLanguage } from "../language/LanguageContext";
import { useWeather } from "../weather/WeatherContext";

const EMOJI: Record<WeatherCondition, string> = {
  sunny: "☀️",
  cloudy: "☁️",
  rain: "🌧️",
  snow: "❄️",
};

// Day labels localized to the current UI language (mirrors the Android string
// resources weather_today/weather_tomorrow). Unsupported locales fall back to en.
const LABELS: Record<string, { today: string; tomorrow: string }> = {
  en: { today: "Today", tomorrow: "Tomorrow" },
  es: { today: "Hoy", tomorrow: "Mañana" },
  ru: { today: "Сегодня", tomorrow: "Завтра" },
};

// Local calendar date (YYYY-MM-DD) offset by days, matching the backend's
// daily[].date keys. Built from local fields (not toISOString, which is UTC).
function isoDate(offsetDays: number): string {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${d.getFullYear()}-${m}-${day}`;
}

// WeatherStrip renders a tiny translucent today/tomorrow forecast: condition,
// temperature high/low, cloud cover and precipitation chance. Renders nothing
// until a forecast with a matching day has been fetched (mirrors Android).
export function WeatherStrip() {
  const weather = useWeather();
  const { language } = useLanguage();
  const labels = LABELS[language] ?? LABELS.en;

  // Label by actual date, not array position: a cache stale across local
  // midnight could leave daily[0] as yesterday. Missing days are skipped.
  const byDate = new Map((weather?.daily ?? []).map((d) => [d.date, d]));
  const cells: { label: string; day: DailyForecast }[] = [];
  const today = byDate.get(isoDate(0));
  const tomorrow = byDate.get(isoDate(1));
  if (today) cells.push({ label: labels.today, day: today });
  if (tomorrow) cells.push({ label: labels.tomorrow, day: tomorrow });
  if (cells.length === 0) return null;

  return (
    <div className="weather-strip">
      {cells.map(({ label, day }, i) => (
        <Fragment key={day.date}>
          {i > 0 && <span className="weather-divider" />}
          <div className="weather-cell">
            <span className="weather-label">{label}</span>
            <span className="weather-temp">
              {EMOJI[day.condition]} {Math.round(day.temp_max_c)}°/
              {Math.round(day.temp_min_c)}°
            </span>
            <span className="weather-meta">
              ☁ {day.cloud_cover_pct}%&nbsp;&nbsp;&nbsp;💧 {day.precip_prob_pct}%
            </span>
          </div>
        </Fragment>
      ))}
    </div>
  );
}
