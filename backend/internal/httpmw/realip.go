// Package httpmw holds small HTTP middlewares specific to this service.
package httpmw

import (
	"net"
	"net/http"
	"strings"
)

// trustedProxies is how many trailing X-Forwarded-For entries are appended by
// infrastructure we control and therefore trust. Behind the GCE external HTTP(S)
// load balancer there is exactly one: the LB sets
//
//	X-Forwarded-For: [<client-supplied>,] <client-ip>, <lb-ip>
//
// and does NOT sanitize whatever the client itself put there, so every entry
// left of <client-ip> is attacker-controlled. The real client is the
// second-to-last entry (the last is the LB). If another trusted proxy is ever
// put in front (e.g. Cloudflare in proxied mode), bump this to match.
const trustedProxies = 1

// RealIP rewrites r.RemoteAddr to the real client IP derived from the trusted
// tail of X-Forwarded-For. Unlike chi's middleware.RealIP it does not trust the
// leftmost XFF value, which is spoofable behind the load balancer. When there is
// no XFF (direct connection, e.g. local dev) the original RemoteAddr is kept.
func RealIP(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if ip := fromXFF(r.Header.Get("X-Forwarded-For")); ip != "" {
			// Port is irrelevant downstream (handlers SplitHostPort then drop it);
			// 0 keeps RemoteAddr a valid host:port.
			r.RemoteAddr = net.JoinHostPort(ip, "0")
		}
		next.ServeHTTP(w, r)
	})
}

// fromXFF returns the trusted client IP from an X-Forwarded-For value, or "" if
// the header is empty or too short to contain a non-proxy entry.
func fromXFF(xff string) string {
	if xff == "" {
		return ""
	}
	parts := strings.Split(xff, ",")
	idx := len(parts) - 1 - trustedProxies
	if idx < 0 {
		return ""
	}
	ip := strings.TrimSpace(parts[idx])
	if net.ParseIP(ip) == nil {
		return ""
	}
	return ip
}
