package repository

import (
	"context"
	"errors"
	"time"

	"github.com/boriay/cld-anki/backend/internal/model"
	"github.com/jackc/pgx/v5"
)

type CardRepo struct {
	db DBTX
}

func NewCardRepo(db DBTX) *CardRepo { return &CardRepo{db: db} }

// WithTx returns a repo bound to the given transaction.
func (r *CardRepo) WithTx(tx pgx.Tx) *CardRepo { return &CardRepo{db: tx} }

const cardCols = `id, deck_id, user_id, front, back, interval, ease_factor, repetitions, next_review_time, created_at, updated_at, deleted_at`

func scanCard(row pgx.Row) (*model.Card, error) {
	c := &model.Card{}
	err := row.Scan(
		&c.ID, &c.DeckID, &c.UserID, &c.Front, &c.Back,
		&c.Interval, &c.EaseFactor, &c.Repetitions, &c.NextReviewTime,
		&c.CreatedAt, &c.UpdatedAt, &c.DeletedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, ErrNotFound
	}
	return c, err
}

// Upsert inserts or updates a card. Verifies deck ownership via subquery.
// Server record wins if its updated_at is newer.
// Upsert returns ErrNotFound when the write affected no rows AND the card does
// not already exist — i.e. an orphan write (parent deck missing/soft-deleted).
// A last-write-wins no-op on an existing card returns nil (success).
func (r *CardRepo) Upsert(ctx context.Context, c *model.Card) error {
	tag, err := r.db.Exec(ctx, `
		INSERT INTO cards (`+cardCols+`)
		-- $12 (deleted_at) is cast explicitly: it's used in "$12 IS NOT NULL"
		-- below where Postgres can't infer the param type for a bare NULL
		-- (non-tombstone card), failing with 42P08. The cast fixes the type
		-- for every use of $12 in this statement.
		SELECT $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12::timestamptz
		-- Deck must exist and be owned by the user. The deck must also be active,
		-- UNLESS this card is itself a tombstone ($12 set) — that path is the
		-- cascade-delete sync and must be allowed under a deleted deck.
		WHERE EXISTS (
			SELECT 1 FROM decks
			WHERE id = $2 AND user_id = $3 AND (deleted_at IS NULL OR $12 IS NOT NULL)
		)
		ON CONFLICT (id) DO UPDATE SET
			front            = EXCLUDED.front,
			back             = EXCLUDED.back,
			interval         = EXCLUDED.interval,
			ease_factor      = EXCLUDED.ease_factor,
			repetitions      = EXCLUDED.repetitions,
			next_review_time = EXCLUDED.next_review_time,
			updated_at       = EXCLUDED.updated_at,
			deleted_at       = EXCLUDED.deleted_at
		WHERE cards.user_id = EXCLUDED.user_id
		  AND cards.updated_at < EXCLUDED.updated_at
		  -- deck_id is immutable via upsert; reject attempts to re-parent a card.
		  AND cards.deck_id = EXCLUDED.deck_id
		  -- Same rule as INSERT: don't let an update resurrect a card (deleted_at
		  -- = null) under a tombstoned deck. Check the EXISTING row's deck_id (not
		  -- the client-supplied EXCLUDED.deck_id, which could be spoofed). Tombstone
		  -- writes are still allowed.
		  AND (
			EXCLUDED.deleted_at IS NOT NULL
			OR EXISTS (
				SELECT 1 FROM decks
				WHERE id = cards.deck_id AND user_id = cards.user_id AND deleted_at IS NULL
			)
		  )`,
		c.ID, c.DeckID, c.UserID, c.Front, c.Back,
		c.Interval, c.EaseFactor, c.Repetitions, c.NextReviewTime,
		c.CreatedAt, c.UpdatedAt, c.DeletedAt,
	)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		// No row written: either an orphan (deck missing/deleted) or a
		// last-write-wins no-op. Only the orphan case is an error.
		var exists bool
		if err := r.db.QueryRow(ctx, `SELECT EXISTS(SELECT 1 FROM cards WHERE id = $1)`, c.ID).Scan(&exists); err != nil {
			return err
		}
		if !exists {
			return ErrNotFound
		}
	}
	return nil
}

func (r *CardRepo) ListByDeck(ctx context.Context, deckID, userID string) ([]*model.Card, error) {
	rows, err := r.db.Query(ctx,
		`SELECT `+cardCols+` FROM cards WHERE deck_id = $1 AND user_id = $2 AND deleted_at IS NULL ORDER BY created_at`,
		deckID, userID,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return collectCards(rows)
}

func (r *CardRepo) GetByID(ctx context.Context, id, userID string) (*model.Card, error) {
	return scanCard(r.db.QueryRow(ctx,
		`SELECT `+cardCols+` FROM cards WHERE id = $1 AND user_id = $2`, id, userID,
	))
}

// UpdateFields applies a partial SM-2 state update in a single guarded statement.
// Only non-nil fields are changed. `deleted_at IS NULL` prevents resurrecting a
// soft-deleted card, and the change predicate skips a no-op write so an idempotent
// review doesn't bump updated_at or re-emit the card in the sync delta. Returns
// the current row unchanged on a no-op, or ErrNotFound if the card is
// absent/tombstoned.
func (r *CardRepo) UpdateFields(
	ctx context.Context,
	id, userID string,
	front, back *string,
	interval *int,
	easeFactor *float64,
	repetitions *int,
	nextReviewTime *time.Time,
) (*model.Card, error) {
	c, err := scanCard(r.db.QueryRow(ctx, `
		UPDATE cards
		SET front            = COALESCE($3, front),
		    back             = COALESCE($4, back),
		    interval         = COALESCE($5, interval),
		    ease_factor      = COALESCE($6, ease_factor),
		    repetitions      = COALESCE($7, repetitions),
		    next_review_time = COALESCE($8, next_review_time),
		    updated_at       = now()
		WHERE id = $1 AND user_id = $2 AND deleted_at IS NULL
		  AND (
		    ($3 IS NOT NULL AND front IS DISTINCT FROM $3) OR
		    ($4 IS NOT NULL AND back IS DISTINCT FROM $4) OR
		    ($5 IS NOT NULL AND interval IS DISTINCT FROM $5) OR
		    ($6 IS NOT NULL AND ease_factor IS DISTINCT FROM $6) OR
		    ($7 IS NOT NULL AND repetitions IS DISTINCT FROM $7) OR
		    ($8 IS NOT NULL AND next_review_time IS DISTINCT FROM $8)
		  )
		RETURNING `+cardCols,
		id, userID, front, back, interval, easeFactor, repetitions, nextReviewTime))
	if errors.Is(err, ErrNotFound) {
		// No row updated: no-op on existing card, or card is absent/tombstoned.
		c, err = r.GetByID(ctx, id, userID)
		if err == nil && c.DeletedAt != nil {
			return nil, ErrNotFound
		}
	}
	return c, err
}

// ChangedSince returns all cards (including soft-deleted) modified after the given time.
func (r *CardRepo) ChangedSince(ctx context.Context, userID string, since time.Time) ([]*model.Card, error) {
	rows, err := r.db.Query(ctx,
		`SELECT `+cardCols+` FROM cards WHERE user_id = $1 AND updated_at >= $2 ORDER BY updated_at`,
		userID, since,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return collectCards(rows)
}

func (r *CardRepo) SoftDelete(ctx context.Context, id, userID string) error {
	now := time.Now().UTC()
	_, err := r.db.Exec(ctx,
		`UPDATE cards SET deleted_at = $1, updated_at = $1 WHERE id = $2 AND user_id = $3 AND deleted_at IS NULL`,
		now, id, userID,
	)
	return err
}

func collectCards(rows pgx.Rows) ([]*model.Card, error) {
	var out []*model.Card
	for rows.Next() {
		c := &model.Card{}
		if err := rows.Scan(
			&c.ID, &c.DeckID, &c.UserID, &c.Front, &c.Back,
			&c.Interval, &c.EaseFactor, &c.Repetitions, &c.NextReviewTime,
			&c.CreatedAt, &c.UpdatedAt, &c.DeletedAt,
		); err != nil {
			return nil, err
		}
		out = append(out, c)
	}
	return out, rows.Err()
}
