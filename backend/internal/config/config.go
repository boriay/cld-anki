package config

import (
	"fmt"
	"os"
)

type Config struct {
	Port              string
	DatabaseURL       string
	FirebaseProjectID string
	// Weather endpoints — optional, default to public services. The client IP
	// is appended to WeatherGeoIPURL as a path segment (e.g. https://ipwho.is/).
	WeatherGeoIPURL    string
	WeatherForecastURL string
}

func Load() (*Config, error) {
	cfg := &Config{
		Port:               getEnv("PORT", "8080"),
		DatabaseURL:        os.Getenv("DATABASE_URL"),
		FirebaseProjectID:  os.Getenv("FIREBASE_PROJECT_ID"),
		WeatherGeoIPURL:    getEnv("WEATHER_GEOIP_URL", "https://ipwho.is/"),
		WeatherForecastURL: getEnv("WEATHER_FORECAST_URL", "https://api.open-meteo.com/v1/forecast"),
	}
	if cfg.DatabaseURL == "" {
		return nil, fmt.Errorf("DATABASE_URL is required")
	}
	if cfg.FirebaseProjectID == "" {
		return nil, fmt.Errorf("FIREBASE_PROJECT_ID is required")
	}
	return cfg, nil
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
