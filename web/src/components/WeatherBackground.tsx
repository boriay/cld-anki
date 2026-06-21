import { type ReactNode } from "react";
import { useWeather } from "../weather/WeatherContext";

// WeatherBackground paints a full-screen gradient keyed to the backend's coarse
// weather state (resolved from the caller's IP). Decorative only: a fallback or
// missing reading falls back to a neutral sky and never blocks the UI.
export function WeatherBackground({ children }: { children: ReactNode }) {
  const weather = useWeather();
  const show = weather && !weather.fallback ? weather : null;

  const cls = show
    ? `bg bg-${show.condition} ${show.is_day ? "bg-day" : "bg-night"}`
    : "bg bg-cloudy bg-day";

  return <div className={cls}>{children}</div>;
}
