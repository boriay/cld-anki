package auth

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"strings"

	firebaseauth "firebase.google.com/go/v4/auth"
)

type contextKey string

const uidKey contextKey = "uid"

func Middleware(client *firebaseauth.Client) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			header := r.Header.Get("Authorization")
			if !strings.HasPrefix(header, "Bearer ") {
				slog.Warn("auth rejected: missing bearer token", "path", r.URL.Path)
				writeUnauthorized(w, "unauthorized")
				return
			}
			token, err := client.VerifyIDToken(r.Context(), strings.TrimPrefix(header, "Bearer "))
			if err != nil {
				slog.Warn("auth rejected: token verification failed", "path", r.URL.Path, "err", err)
				writeUnauthorized(w, "invalid token")
				return
			}
			next.ServeHTTP(w, r.WithContext(context.WithValue(r.Context(), uidKey, token.UID)))
		})
	}
}

func UserIDFromCtx(ctx context.Context) string {
	uid, _ := ctx.Value(uidKey).(string)
	return uid
}

func writeUnauthorized(w http.ResponseWriter, msg string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusUnauthorized)
	json.NewEncoder(w).Encode(map[string]string{"error": msg})
}
