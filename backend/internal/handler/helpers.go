package handler

import (
	"encoding/json"
	"log/slog"
	"net/http"
)

func jsonOK(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(v)
}

func jsonError(w http.ResponseWriter, msg string, code int) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(map[string]string{"error": msg})
}

// internalError logs the real cause server-side and returns a generic message,
// so internal details (SQL, schema) never leak to the client.
func internalError(w http.ResponseWriter, err error) {
	slog.Error("internal error", "err", err)
	jsonError(w, "internal error", http.StatusInternalServerError)
}
