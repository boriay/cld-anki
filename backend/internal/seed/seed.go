// Package seed inserts the default decks and cards for a brand-new user so the
// web client matches the Android client's initial content. The card data is
// generated from the Android source (data.go); see scripts/gen_seed.py.
package seed

import (
	"context"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// Seed inserts the default decks/cards for a user that has none, returning true
// when it did. It is a no-op (false) when the user already has any deck row —
// including soft-deleted ones — so it never re-seeds after the user clears their
// decks, and never duplicates decks an Android client already synced up.
//
// Deck and card IDs are UUID v5 (SHA-1) derived from the userID and content key
// (language, card front). The same user always gets the same IDs regardless of
// which client seeds first, so a concurrent push from the Android client upserts
// idempotently instead of inserting duplicate rows.
func Seed(ctx context.Context, pool *pgxpool.Pool, userID string) (bool, error) {
	tx, err := pool.Begin(ctx)
	if err != nil {
		return false, err
	}
	defer tx.Rollback(ctx) // no-op once committed

	// Serialize concurrent seeds for the same user (e.g. two browser tabs on a
	// fresh account) so the EXISTS check below can't race into a double insert.
	// The lock is released at transaction end.
	if _, err := tx.Exec(ctx, `SELECT pg_advisory_xact_lock(hashtext($1))`, userID); err != nil {
		return false, err
	}

	var exists bool
	if err := tx.QueryRow(ctx,
		`SELECT EXISTS(SELECT 1 FROM decks WHERE user_id = $1)`, userID,
	).Scan(&exists); err != nil {
		return false, err
	}
	if exists {
		return false, nil
	}

	now := time.Now().UTC()
	batch := &pgx.Batch{}
	for _, d := range seedDecks {
		// UUID v5: deterministic per (user, language) so the same user always
		// gets the same deck ID on every client that seeds, making concurrent
		// pushes from Android upsert idempotently.
		deckID := uuid.NewSHA1(uuid.NameSpaceDNS, []byte(userID+":seed:deck:"+d.Language)).String()
		batch.Queue(
			`INSERT INTO decks (id, user_id, name, language, pinned, created_at, updated_at)
			 VALUES ($1, $2, $3, $4, FALSE, $5, $5)`,
			deckID, userID, d.Name, d.Language, now,
		)
		for _, c := range seedCards {
			// UUID v5: deterministic per (user, language, front).
			cardID := uuid.NewSHA1(uuid.NameSpaceDNS, []byte(userID+":seed:card:"+d.Language+":"+c.Front)).String()
			batch.Queue(
				`INSERT INTO cards
				   (id, deck_id, user_id, front, back, interval, ease_factor, repetitions, next_review_time, created_at, updated_at)
				 VALUES ($1, $2, $3, $4, $5, 1, 2.5, 0, $6, $6, $6)`,
				cardID, deckID, userID, c.Front, c.backFor(d.Language), now,
			)
		}
	}

	br := tx.SendBatch(ctx, batch)
	// Drain every queued result before closing; the first error fails the seed.
	for i := 0; i < batch.Len(); i++ {
		if _, err := br.Exec(); err != nil {
			br.Close()
			return false, err
		}
	}
	if err := br.Close(); err != nil {
		return false, err
	}
	return true, tx.Commit(ctx)
}

// backFor maps a deck language tag to the matching translation column. Catalan
// fronts are shared across decks; only the back differs (mirrors Android).
func (c seedCardData) backFor(language string) string {
	switch language {
	case "es":
		return c.Es
	case "ru":
		return c.Ru
	default:
		return c.En
	}
}
