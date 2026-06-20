package handler

import (
	"log/slog"
	"net"
	"net/http"

	"github.com/boriay/cld-anki/backend/internal/weather"
)

type WeatherHandler struct {
	svc *weather.Service
}

func NewWeatherHandler(svc *weather.Service) *WeatherHandler {
	return &WeatherHandler{svc: svc}
}

// Current returns the coarse weather state for the caller's approximate
// location (resolved from its IP). The per-location cache in the service caps
// upstream calls at one per hour, so this endpoint is cheap to poll.
func (h *WeatherHandler) Current(w http.ResponseWriter, r *http.Request) {
	wx, err := h.svc.ForIP(r.Context(), clientIP(r))
	if err != nil {
		// Weather is decorative — never fail the screen. Return a neutral
		// default the client can render, and log the real cause server-side.
		slog.Error("weather lookup", "err", err)
		jsonOK(w, weather.Weather{Condition: weather.Sunny, IsDay: true})
		return
	}
	jsonOK(w, wx)
}

// clientIP returns the caller's public IP, or "" when the address is loopback
// or private — in which case the geolocation provider falls back to the
// request's own source IP. chi's RealIP middleware has already resolved
// X-Forwarded-For / X-Real-IP into RemoteAddr upstream.
func clientIP(r *http.Request) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		host = r.RemoteAddr
	}
	ip := net.ParseIP(host)
	if ip == nil || ip.IsLoopback() || ip.IsPrivate() || ip.IsUnspecified() {
		return ""
	}
	return host
}
