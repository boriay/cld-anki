// Mirrors the backend JSON contract (backend/internal/model/model.go and the
// weather service). Timestamps are RFC3339 strings on the wire.

export interface Deck {
  id: string;
  name: string;
  language: string | null;
  pinned: boolean;
  created_at: string;
  updated_at: string;
  deleted_at?: string | null;
}

export interface Card {
  id: string;
  deck_id: string;
  front: string;
  back: string;
  interval: number;
  ease_factor: number;
  repetitions: number;
  next_review_time: string;
  created_at: string;
  updated_at: string;
  deleted_at?: string | null;
}

export type WeatherCondition = "sunny" | "cloudy" | "rain" | "snow";

export interface DailyForecast {
  date: string;
  condition: WeatherCondition;
  temp_max_c: number;
  temp_min_c: number;
  precipitation_mm: number;
  precip_prob_pct: number;
  cloud_cover_pct: number;
}

export interface Weather {
  condition: WeatherCondition;
  is_day: boolean;
  temperature_c: number;
  weather_code: number;
  city?: string;
  fetched_at: string;
  daily?: DailyForecast[];
  fallback?: boolean;
}
