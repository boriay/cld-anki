import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { api } from "../api/client";
import type { Weather } from "../api/types";

// Shares one weather fetch (IP-resolved server-side) between the background
// gradient and the forecast strip, so they stay in sync off a single request.
// Mirrors Android hoisting weatherState to MainActivity for both consumers.
const WeatherCtx = createContext<Weather | null>(null);

export function WeatherProvider({ children }: { children: ReactNode }) {
  const [weather, setWeather] = useState<Weather | null>(null);

  useEffect(() => {
    let alive = true;
    api
      .weather()
      .then((w) => {
        if (alive) setWeather(w);
      })
      .catch(() => {
        /* decorative — ignore */
      });
    return () => {
      alive = false;
    };
  }, []);

  return <WeatherCtx.Provider value={weather}>{children}</WeatherCtx.Provider>;
}

export function useWeather(): Weather | null {
  return useContext(WeatherCtx);
}
