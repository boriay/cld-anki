package auth

import (
	"context"
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
				http.Error(w, `{"error":"unauthorized"}`, http.StatusUnauthorized)
				return
			}
			token, err := client.VerifyIDToken(r.Context(), strings.TrimPrefix(header, "Bearer "))
			if err != nil {
				slog.Warn("auth rejected: token verification failed", "path", r.URL.Path, "err", err)
				http.Error(w, `{"error":"invalid token"}`, http.StatusUnauthorized)
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
