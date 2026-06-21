package httpmw

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestFromXFF(t *testing.T) {
	tests := []struct {
		name string
		xff  string
		want string
	}{
		{"empty", "", ""},
		// Single entry can't be split into client + LB, so nothing is trusted.
		{"lb only", "34.160.37.42", ""},
		{"client and lb", "203.0.113.7, 34.160.37.42", "203.0.113.7"},
		// Spoofed leftmost value must be ignored; trust only the second-to-last.
		{"spoofed prefix", "1.2.3.4, 203.0.113.7, 34.160.37.42", "203.0.113.7"},
		{"spaces trimmed", "203.0.113.7 ,  34.160.37.42", "203.0.113.7"},
		{"non-ip trusted slot", "garbage, 34.160.37.42", ""},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := fromXFF(tt.xff); got != tt.want {
				t.Errorf("fromXFF(%q) = %q, want %q", tt.xff, got, tt.want)
			}
		})
	}
}

func TestRealIPRewritesRemoteAddr(t *testing.T) {
	var seen string
	h := RealIP(http.HandlerFunc(func(_ http.ResponseWriter, r *http.Request) {
		seen = r.RemoteAddr
	}))

	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.RemoteAddr = "10.0.0.1:5555" // LB's view (private node IP)
	req.Header.Set("X-Forwarded-For", "1.2.3.4, 203.0.113.7, 34.160.37.42")
	h.ServeHTTP(httptest.NewRecorder(), req)

	if want := "203.0.113.7:0"; seen != want {
		t.Errorf("RemoteAddr = %q, want %q", seen, want)
	}
}

func TestRealIPKeepsRemoteAddrWithoutXFF(t *testing.T) {
	var seen string
	h := RealIP(http.HandlerFunc(func(_ http.ResponseWriter, r *http.Request) {
		seen = r.RemoteAddr
	}))

	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.RemoteAddr = "198.51.100.9:4444"
	h.ServeHTTP(httptest.NewRecorder(), req)

	if want := "198.51.100.9:4444"; seen != want {
		t.Errorf("RemoteAddr = %q, want %q", seen, want)
	}
}
