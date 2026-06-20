// Package weather resolves a client's approximate location from its IP and
// fetches current conditions from Open-Meteo. Results are cached per location
// so the upstream forecast API is hit at most once per hour for a given place,
// regardless of how many clients ask.
package weather

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"strings"
	"sync"
	"time"

	"golang.org/x/sync/singleflight"
)

// Condition is the coarse, UI-facing weather state the client renders a
// background for. The full WMO weather-code space is collapsed into these four.
type Condition string

const (
	Sunny  Condition = "sunny"
	Cloudy Condition = "cloudy"
	Rain   Condition = "rain"
	Snow   Condition = "snow"
)

// Weather is the response returned to the client.
type Weather struct {
	Condition   Condition       `json:"condition"`
	IsDay       bool            `json:"is_day"`
	Temperature float64         `json:"temperature_c"`
	WeatherCode int             `json:"weather_code"`
	City        string          `json:"city,omitempty"`
	FetchedAt   time.Time       `json:"fetched_at"`
	Daily       []DailyForecast `json:"daily,omitempty"`
	// Fallback is true only when the handler couldn't resolve real weather and
	// returned a neutral default; the client uses it to avoid overwriting its
	// last-good cache. Omitted (false) on normal responses.
	Fallback bool `json:"fallback,omitempty"`
}

// DailyForecast is a coarse per-day summary (today and tomorrow).
type DailyForecast struct {
	Date          string    `json:"date"` // YYYY-MM-DD, local
	Condition     Condition `json:"condition"`
	TempMax       float64   `json:"temp_max_c"`
	TempMin       float64   `json:"temp_min_c"`
	Precipitation float64   `json:"precipitation_mm"`
	PrecipProb    int       `json:"precip_prob_pct"`
	CloudCover    int       `json:"cloud_cover_pct"`
}

// conditionFromCode maps a WMO weather code to a coarse Condition.
// Ranges follow Open-Meteo's documented code table:
// 0–1 clear, 2–3/45–48 cloudy/fog, 51–67/80–82/95–99 rain, 71–77/85–86 snow.
func conditionFromCode(code int) Condition {
	switch {
	case code >= 71 && code <= 77, code == 85, code == 86:
		return Snow
	case code >= 51 && code <= 67, code >= 80 && code <= 82, code >= 95 && code <= 99:
		return Rain
	case code == 0, code == 1:
		return Sunny
	default:
		// 2–3 (cloudy), 45–48 (fog) and anything unrecognised fall back here.
		return Cloudy
	}
}

// dailyRaw mirrors Open-Meteo's column-oriented "daily" block (parallel arrays).
type dailyRaw struct {
	Time          []string  `json:"time"`
	TempMax       []float64 `json:"temperature_2m_max"`
	TempMin       []float64 `json:"temperature_2m_min"`
	PrecipSum     []float64 `json:"precipitation_sum"`
	PrecipProbMax []int     `json:"precipitation_probability_max"`
	CloudCover    []int     `json:"cloud_cover_mean"`
	WeatherCode   []int     `json:"weather_code"`
}

// buildDaily transposes the parallel arrays into one struct per day, tolerating
// any array being shorter than the day count (defensive against partial data).
func buildDaily(d dailyRaw) []DailyForecast {
	out := make([]DailyForecast, 0, len(d.Time))
	for i := range d.Time {
		df := DailyForecast{Date: d.Time[i]}
		if i < len(d.WeatherCode) {
			df.Condition = conditionFromCode(d.WeatherCode[i])
		}
		if i < len(d.TempMax) {
			df.TempMax = d.TempMax[i]
		}
		if i < len(d.TempMin) {
			df.TempMin = d.TempMin[i]
		}
		if i < len(d.PrecipSum) {
			df.Precipitation = d.PrecipSum[i]
		}
		if i < len(d.PrecipProbMax) {
			df.PrecipProb = d.PrecipProbMax[i]
		}
		if i < len(d.CloudCover) {
			df.CloudCover = d.CloudCover[i]
		}
		out = append(out, df)
	}
	return out
}

// Service fetches and caches weather by client IP.
type Service struct {
	http        *http.Client
	geoURL      string // base URL for IP geolocation; the IP is appended as a path segment
	forecastURL string // Open-Meteo forecast endpoint
	ttl         time.Duration

	mu     sync.Mutex
	geoC   map[string]geoEntry // keyed by client IP
	wxC    map[string]wxEntry  // keyed by rounded "lat,lon"
	geoTTL time.Duration

	// Collapse concurrent cache-miss fetches for the same key into one upstream
	// call, so a burst of clients can't exceed the once-per-ttl invariant.
	sfGeo singleflight.Group
	sfWx  singleflight.Group
}

type geoEntry struct {
	loc    geoLocation
	expire time.Time
}

type wxEntry struct {
	wx     Weather
	expire time.Time
}

type geoLocation struct {
	Lat  float64
	Lon  float64
	City string
}

const (
	// Opportunistic eviction bounds: when a cache exceeds maxCacheEntries we
	// drop entries that expired more than staleRetention ago. Entries are kept
	// past their TTL on purpose so stale-on-error can still serve them; only
	// entries too old to be useful as a fallback are pruned.
	maxCacheEntries = 4096
	staleRetention  = 24 * time.Hour
)

// NewService builds a Service. geoURL and forecastURL fall back to sensible
// public defaults when empty. ttl bounds how stale a per-location forecast may
// be (and thus the minimum interval between upstream calls for that location).
func NewService(geoURL, forecastURL string, ttl time.Duration) *Service {
	if geoURL == "" {
		geoURL = "https://ipwho.is/"
	}
	// The IP is appended as a path segment, so the base must end in "/".
	// Normalize here so a config like "https://ipwho.is" (no slash) can't turn
	// into "https://ipwho.is1.2.3.4".
	if !strings.HasSuffix(geoURL, "/") {
		geoURL += "/"
	}
	if forecastURL == "" {
		forecastURL = "https://api.open-meteo.com/v1/forecast"
	}
	if ttl <= 0 {
		ttl = time.Hour
	}
	return &Service{
		http:        &http.Client{Timeout: 8 * time.Second},
		geoURL:      geoURL,
		forecastURL: forecastURL,
		ttl:         ttl,
		geoC:        make(map[string]geoEntry),
		wxC:         make(map[string]wxEntry),
		// IP→city is stable; cache it far longer than the forecast itself.
		geoTTL: 24 * time.Hour,
	}
}

// ForIP returns current weather for the location of the given client IP. An
// empty ip lets the geolocation provider fall back to the request's source IP
// (useful in local/dev where the client address is loopback or private).
func (s *Service) ForIP(ctx context.Context, ip string) (Weather, error) {
	loc, err := s.geolocate(ctx, ip)
	if err != nil {
		return Weather{}, err
	}
	return s.forecast(ctx, loc)
}

func (s *Service) geolocate(ctx context.Context, ip string) (geoLocation, error) {
	now := time.Now()
	s.mu.Lock()
	cached, ok := s.geoC[ip]
	s.mu.Unlock()
	if ok && now.Before(cached.expire) {
		return cached.loc, nil
	}

	// singleflight collapses a burst of concurrent misses into one upstream call.
	v, err, _ := s.sfGeo.Do(ip, func() (any, error) { return s.fetchGeo(ctx, ip) })
	if err != nil {
		if ok {
			// Stale-on-error: a previously resolved location beats failing the
			// whole request when the geoip provider is briefly unavailable.
			slog.Warn("geoip upstream failed, serving stale location", "err", err)
			return cached.loc, nil
		}
		return geoLocation{}, err
	}

	loc := v.(geoLocation)
	s.mu.Lock()
	s.geoC[ip] = geoEntry{loc: loc, expire: now.Add(s.geoTTL)}
	if len(s.geoC) > maxCacheEntries {
		for k, e := range s.geoC {
			if now.After(e.expire.Add(staleRetention)) {
				delete(s.geoC, k)
			}
		}
	}
	s.mu.Unlock()
	return loc, nil
}

func (s *Service) fetchGeo(ctx context.Context, ip string) (geoLocation, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, s.geoURL+ip, nil)
	if err != nil {
		return geoLocation{}, err
	}
	resp, err := s.http.Do(req)
	if err != nil {
		return geoLocation{}, fmt.Errorf("geoip request: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return geoLocation{}, fmt.Errorf("geoip status %d", resp.StatusCode)
	}

	var body struct {
		Success   bool    `json:"success"`
		Message   string  `json:"message"`
		Latitude  float64 `json:"latitude"`
		Longitude float64 `json:"longitude"`
		City      string  `json:"city"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		return geoLocation{}, fmt.Errorf("geoip decode: %w", err)
	}
	if !body.Success {
		return geoLocation{}, fmt.Errorf("geoip failed: %s", body.Message)
	}
	return geoLocation{Lat: body.Latitude, Lon: body.Longitude, City: body.City}, nil
}

func (s *Service) forecast(ctx context.Context, loc geoLocation) (Weather, error) {
	// Round to ~1km so nearby clients share a cache slot and we never exceed
	// one upstream call per location per ttl.
	key := fmt.Sprintf("%.2f,%.2f", loc.Lat, loc.Lon)

	now := time.Now()
	s.mu.Lock()
	cached, ok := s.wxC[key]
	s.mu.Unlock()
	if ok && now.Before(cached.expire) {
		return cached.wx, nil
	}

	v, err, _ := s.sfWx.Do(key, func() (any, error) { return s.fetchForecast(ctx, loc, now) })
	if err != nil {
		if ok {
			// Stale-on-error: the last known weather is more useful than a
			// neutral default when Open-Meteo is briefly unavailable.
			slog.Warn("forecast upstream failed, serving stale weather", "err", err)
			return cached.wx, nil
		}
		return Weather{}, err
	}

	wx := v.(Weather)
	s.mu.Lock()
	s.wxC[key] = wxEntry{wx: wx, expire: now.Add(s.ttl)}
	if len(s.wxC) > maxCacheEntries {
		for k, e := range s.wxC {
			if now.After(e.expire.Add(staleRetention)) {
				delete(s.wxC, k)
			}
		}
	}
	s.mu.Unlock()
	return wx, nil
}

func (s *Service) fetchForecast(ctx context.Context, loc geoLocation, now time.Time) (Weather, error) {
	// timezone=auto so the daily buckets align to the location's local days
	// (today/tomorrow); forecast_days=2 keeps the payload minimal.
	url := fmt.Sprintf("%s?latitude=%.4f&longitude=%.4f"+
		"&current=temperature_2m,is_day,weather_code"+
		"&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,"+
		"precipitation_probability_max,cloud_cover_mean,weather_code"+
		"&forecast_days=2&timezone=auto",
		s.forecastURL, loc.Lat, loc.Lon)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return Weather{}, err
	}
	resp, err := s.http.Do(req)
	if err != nil {
		return Weather{}, fmt.Errorf("forecast request: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return Weather{}, fmt.Errorf("forecast status %d", resp.StatusCode)
	}

	var body struct {
		Current struct {
			Temperature float64 `json:"temperature_2m"`
			IsDay       int     `json:"is_day"`
			WeatherCode int     `json:"weather_code"`
		} `json:"current"`
		Daily dailyRaw `json:"daily"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		return Weather{}, fmt.Errorf("forecast decode: %w", err)
	}

	return Weather{
		Condition:   conditionFromCode(body.Current.WeatherCode),
		IsDay:       body.Current.IsDay == 1,
		Temperature: body.Current.Temperature,
		WeatherCode: body.Current.WeatherCode,
		City:        loc.City,
		FetchedAt:   now.UTC(),
		Daily:       buildDaily(body.Daily),
	}, nil
}
