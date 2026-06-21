package handler

import (
	"encoding/json"
	"errors"
	"net/http"
	"time"

	"github.com/boriay/cld-anki/backend/internal/auth"
	"github.com/boriay/cld-anki/backend/internal/model"
	"github.com/boriay/cld-anki/backend/internal/repository"
	"github.com/jackc/pgx/v5/pgxpool"
)

type SyncHandler struct {
	pool  *pgxpool.Pool
	decks *repository.DeckRepo
	cards *repository.CardRepo
}

func NewSyncHandler(pool *pgxpool.Pool, decks *repository.DeckRepo, cards *repository.CardRepo) *SyncHandler {
	return &SyncHandler{pool: pool, decks: decks, cards: cards}
}

type syncRequest struct {
	LastSyncedAt *time.Time    `json:"last_synced_at"`
	Decks        []*model.Deck `json:"decks"`
	Cards        []*model.Card `json:"cards"`
}

type syncResponse struct {
	SyncedAt time.Time     `json:"synced_at"`
	Decks    []*model.Deck `json:"decks"`
	Cards    []*model.Card `json:"cards"`
}

// Sync applies client-side changes and returns server-side changes since last_synced_at.
// Conflict resolution: last-write-wins based on updated_at.
// Decks are upserted before cards to satisfy the FK constraint.
func (h *SyncHandler) Sync(w http.ResponseWriter, r *http.Request) {
	uid := auth.UserIDFromCtx(r.Context())
	// Cap the request body to bound memory/CPU per sync (10 MiB is far above a
	// realistic flashcard delta).
	r.Body = http.MaxBytesReader(w, r.Body, 10<<20)
	var req syncRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		var maxErr *http.MaxBytesError
		if errors.As(err, &maxErr) {
			jsonError(w, "request too large", http.StatusRequestEntityTooLarge)
			return
		}
		jsonError(w, "invalid body", http.StatusBadRequest)
		return
	}

	ctx := r.Context()

	// Snapshot time before applying changes — server changes after this point
	// are safe to fetch in the next sync cycle.
	syncedAt := time.Now().UTC()

	// Apply all client changes atomically: a mid-batch failure must not leave
	// the DB half-updated, otherwise the client's lastSyncedAt would skip rows.
	tx, err := h.pool.Begin(ctx)
	if err != nil {
		internalError(w, err)
		return
	}
	defer tx.Rollback(ctx) // no-op once committed

	decksTx := h.decks.WithTx(tx)
	cardsTx := h.cards.WithTx(tx)

	// Apply client decks first (cards depend on them via FK).
	for _, d := range req.Decks {
		d.UserID = uid // enforce ownership regardless of payload
		// Don't let a client with a fast/forged clock set a far-future
		// updated_at to win last-write-wins forever; cap it at server time.
		// (Past timestamps are left intact for legitimate offline edits.)
		if d.UpdatedAt.After(syncedAt) {
			d.UpdatedAt = syncedAt
		}
		if err := decksTx.Upsert(ctx, d); err != nil {
			internalError(w, err)
			return
		}
	}
	for _, c := range req.Cards {
		c.UserID = uid
		if c.UpdatedAt.After(syncedAt) {
			c.UpdatedAt = syncedAt
		}
		// Sanitise SM-2 fields (shared bounds with PUT /cards/{id}) so a buggy
		// client can't overflow the INTEGER interval column via the sync path.
		clampCardSM2(c)
		if err := cardsTx.Upsert(ctx, c); err != nil {
			// Orphan card (deck missing/soft-deleted) — skip it rather than
			// aborting the whole batch, so one bad row can't block the client's
			// entire sync. Other (real) errors still fail the request.
			if errors.Is(err, repository.ErrNotFound) {
				continue
			}
			internalError(w, err)
			return
		}
	}

	if err := tx.Commit(ctx); err != nil {
		internalError(w, err)
		return
	}

	since := time.Time{}
	if req.LastSyncedAt != nil {
		since = *req.LastSyncedAt
	}
	// A future cursor (corrupt prefs / malicious client) would return no rows and
	// make the client skip every change up to now. Fall back to a full resync so
	// the protocol self-heals.
	if since.After(syncedAt) {
		since = time.Time{}
	}

	decks, err := h.decks.ChangedSince(ctx, uid, since)
	if err != nil {
		internalError(w, err)
		return
	}
	cards, err := h.cards.ChangedSince(ctx, uid, since)
	if err != nil {
		internalError(w, err)
		return
	}

	if decks == nil {
		decks = []*model.Deck{}
	}
	if cards == nil {
		cards = []*model.Card{}
	}

	jsonOK(w, &syncResponse{SyncedAt: syncedAt, Decks: decks, Cards: cards})
}
