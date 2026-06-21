package handler

import (
	"net/http"

	"github.com/boriay/cld-anki/backend/internal/auth"
	"github.com/boriay/cld-anki/backend/internal/seed"
	"github.com/jackc/pgx/v5/pgxpool"
)

type SeedHandler struct {
	pool *pgxpool.Pool
}

func NewSeedHandler(pool *pgxpool.Pool) *SeedHandler {
	return &SeedHandler{pool: pool}
}

// Seed populates the default decks/cards for a user that has none. Idempotent:
// callers can hit it on every sign-in; existing accounts get a no-op. The web
// client calls this once when it loads the deck list.
func (h *SeedHandler) Seed(w http.ResponseWriter, r *http.Request) {
	uid := auth.UserIDFromCtx(r.Context())
	seeded, err := seed.Seed(r.Context(), h.pool, uid)
	if err != nil {
		internalError(w, err)
		return
	}
	jsonOK(w, map[string]bool{"seeded": seeded})
}
