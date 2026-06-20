package weather

import (
	"context"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"
)

func TestConditionFromCode(t *testing.T) {
	cases := []struct {
		code int
		want Condition
	}{
		{0, Sunny}, {1, Sunny},
		{2, Cloudy}, {3, Cloudy}, {45, Cloudy}, {48, Cloudy},
		{51, Rain}, {61, Rain}, {67, Rain}, {80, Rain}, {82, Rain}, {95, Rain}, {99, Rain},
		{71, Snow}, {75, Snow}, {77, Snow}, {85, Snow}, {86, Snow},
		{999, Cloudy}, // unknown falls back to cloudy
	}
	for _, c := range cases {
		if got := conditionFromCode(c.code); got != c.want {
			t.Errorf("conditionFromCode(%d) = %q, want %q", c.code, got, c.want)
		}
	}
}

func TestForIP_CachesPerLocation(t *testing.T) {
	geo := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Write([]byte(`{"success":true,"latitude":52.52,"longitude":13.41,"city":"Berlin"}`))
	}))
	defer geo.Close()

	var forecastHits int
	forecast := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		forecastHits++
		w.Write([]byte(`{"current":{"temperature_2m":17.3,"is_day":1,"weather_code":61},` +
			`"daily":{"time":["2026-06-20","2026-06-21"],` +
			`"temperature_2m_max":[29.3,31.7],"temperature_2m_min":[20.1,21.8],` +
			`"precipitation_sum":[0.2,0.0],"precipitation_probability_max":[30,25],` +
			`"cloud_cover_mean":[68,42],"weather_code":[61,3]}}`))
	}))
	defer forecast.Close()

	s := NewService(geo.URL+"/", forecast.URL, time.Hour)

	wx, err := s.ForIP(context.Background(), "1.2.3.4")
	if err != nil {
		t.Fatalf("ForIP: %v", err)
	}
	if wx.Condition != Rain || !wx.IsDay || wx.City != "Berlin" {
		t.Fatalf("unexpected weather: %+v", wx)
	}
	if len(wx.Daily) != 2 {
		t.Fatalf("expected 2 daily entries, got %d", len(wx.Daily))
	}
	today := wx.Daily[0]
	if today.Date != "2026-06-20" || today.Condition != Rain || today.TempMax != 29.3 ||
		today.CloudCover != 68 || today.PrecipProb != 30 {
		t.Errorf("unexpected today forecast: %+v", today)
	}
	if wx.Daily[1].Condition != Cloudy {
		t.Errorf("expected tomorrow cloudy, got %q", wx.Daily[1].Condition)
	}

	// Second call for the same IP must be served from cache (no extra upstream hit).
	if _, err := s.ForIP(context.Background(), "1.2.3.4"); err != nil {
		t.Fatalf("ForIP second: %v", err)
	}
	if forecastHits != 1 {
		t.Errorf("forecast hit %d times, want 1 (cache miss)", forecastHits)
	}
}

func TestForIP_StaleOnError(t *testing.T) {
	geo := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Write([]byte(`{"success":true,"latitude":52.52,"longitude":13.41,"city":"Berlin"}`))
	}))
	defer geo.Close()

	var fail atomic.Bool
	forecast := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		if fail.Load() {
			http.Error(w, "boom", http.StatusInternalServerError)
			return
		}
		w.Write([]byte(`{"current":{"temperature_2m":17.3,"is_day":1,"weather_code":61},` +
			`"daily":{"time":["2026-06-20"],"temperature_2m_max":[29.3],` +
			`"temperature_2m_min":[20.1],"precipitation_sum":[0.2],` +
			`"precipitation_probability_max":[30],"cloud_cover_mean":[68],"weather_code":[61]}}`))
	}))
	defer forecast.Close()

	// Tiny TTL so the cache expires between the two calls.
	s := NewService(geo.URL+"/", forecast.URL, 10*time.Millisecond)

	first, err := s.ForIP(context.Background(), "1.2.3.4")
	if err != nil {
		t.Fatalf("first ForIP: %v", err)
	}

	time.Sleep(20 * time.Millisecond) // let the forecast cache expire
	fail.Store(true)                  // now Open-Meteo is "down"

	stale, err := s.ForIP(context.Background(), "1.2.3.4")
	if err != nil {
		t.Fatalf("stale ForIP returned error instead of serving stale: %v", err)
	}
	if stale.Temperature != first.Temperature || stale.Condition != first.Condition ||
		len(stale.Daily) != len(first.Daily) {
		t.Errorf("expected stale weather equal to first %+v, got %+v", first, stale)
	}
}
