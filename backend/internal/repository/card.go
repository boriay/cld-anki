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
func (r *CardRepo) Upsert(ctx context.Context, c *model.Card) error {
	_, err := r.db.Exec(ctx, `
		INSERT INTO cards (`+cardCols+`)
		SELECT $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12
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
		  AND cards.updated_at < EXCLUDED.updated_at`,
		c.ID, c.DeckID, c.UserID, c.Front, c.Back,
		c.Interval, c.EaseFactor, c.Repetitions, c.NextReviewTime,
		c.CreatedAt, c.UpdatedAt, c.DeletedAt,
	)
	return err
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
