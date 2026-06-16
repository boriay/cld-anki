package handler

import (
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
)

// maxBodyBytes caps single-resource request bodies (sync uses its own larger cap).
const maxBodyBytes = 1 << 20 // 1 MiB

// decodeBody caps and decodes a JSON request body, writing the proper error
// response on failure (413 too large, 400 malformed). Returns false if the
// caller should stop.
func decodeBody(w http.ResponseWriter, r *http.Request, dst any) bool {
	r.Body = http.MaxBytesReader(w, r.Body, maxBodyBytes)
	if err := json.NewDecoder(r.Body).Decode(dst); err != nil {
		var maxErr *http.MaxBytesError
		if errors.As(err, &maxErr) {
			jsonError(w, "request too large", http.StatusRequestEntityTooLarge)
		} else {
			jsonError(w, "invalid body", http.StatusBadRequest)
		}
		return false
	}
	return true
}

func jsonOK(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(v)
}

// jsonStatus writes a JSON body with an explicit status code. Content-Type must
// be set before WriteHeader, otherwise the header is dropped.
func jsonStatus(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
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
