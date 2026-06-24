package repository

import (
	"context"
	"errors"
	"time"

	"github.com/boriay/cld-anki/backend/internal/model"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

var ErrNotFound = errors.New("not found")

// DBTX is satisfied by both *pgxpool.Pool and pgx.Tx, letting repository methods
// run either on the pool directly or inside a transaction (see WithTx).
type DBTX interface {
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
}

type DeckRepo struct {
	db DBTX
}

func NewDeckRepo(db DBTX) *DeckRepo { return &DeckRepo{db: db} }

// WithTx returns a repo bound to the given transaction.
func (r *DeckRepo) WithTx(tx pgx.Tx) *DeckRepo { return &DeckRepo{db: tx} }

const deckCols = `id, user_id, name, language, pinned, created_at, updated_at, deleted_at`

func scanDeck(row pgx.Row) (*model.Deck, error) {
	d := &model.Deck{}
	err := row.Scan(&d.ID, &d.UserID, &d.Name, &d.Language, &d.Pinned, &d.CreatedAt, &d.UpdatedAt, &d.DeletedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, ErrNotFound
	}
	return d, err
}

// Upsert inserts or updates a deck. Server record wins if its updated_at is newer.
func (r *DeckRepo) Upsert(ctx context.Context, d *model.Deck) error {
	_, err := r.db.Exec(ctx, `
		INSERT INTO decks (`+deckCols+`)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
		ON CONFLICT (id) DO UPDATE SET
			name        = EXCLUDED.name,
			language    = COALESCE(decks.language, EXCLUDED.language),
			pinned      = (decks.pinned OR EXCLUDED.pinned),
			updated_at  = EXCLUDED.updated_at,
			deleted_at  = EXCLUDED.deleted_at
		WHERE decks.user_id = EXCLUDED.user_id
		  AND decks.updated_at < EXCLUDED.updated_at`,
		d.ID, d.UserID, d.Name, d.Language, d.Pinned, d.CreatedAt, d.UpdatedAt, d.DeletedAt,
	)
	return err
}

func (r *DeckRepo) ListByUser(ctx context.Context, userID string) ([]*model.Deck, error) {
	rows, err := r.db.Query(ctx,
		`SELECT `+deckCols+` FROM decks WHERE user_id = $1 AND deleted_at IS NULL ORDER BY created_at`,
		userID,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return collectDecks(rows)
}

func (r *DeckRepo) GetByID(ctx context.Context, id, userID string) (*model.Deck, error) {
	return scanDeck(r.db.QueryRow(ctx,
		`SELECT `+deckCols+` FROM decks WHERE id = $1 AND user_id = $2`, id, userID,
	))
}

// UpdateFields applies an optional rename and/or a one-way pin in a single
// guarded statement. `deleted_at IS NULL` prevents a stale read-modify-write
// from resurrecting a tombstoned deck, and the change predicate skips a no-op
// write so an idempotent pin doesn't bump updated_at or re-emit the deck in the
// sync delta. Returns the current row unchanged on a no-op, or ErrNotFound if no
// active deck matched.
func (r *DeckRepo) UpdateFields(ctx context.Context, id, userID string, name *string, pin bool) (*model.Deck, error) {
	d, err := scanDeck(r.db.QueryRow(ctx, `
		UPDATE decks
		-- $3 (name) is cast explicitly: a nil Go *string becomes an untyped NULL,
		-- and Postgres raises 42P08 when it cannot infer the param type. ::text fixes it.
		SET name = COALESCE($3::text, name),
			pinned = (pinned OR $4),
			updated_at = now()
		WHERE id = $1 AND user_id = $2 AND deleted_at IS NULL
		  AND (($3::text IS NOT NULL AND name IS DISTINCT FROM $3::text) OR ($4 AND NOT pinned))
		RETURNING `+deckCols, id, userID, name, pin))
	if errors.Is(err, ErrNotFound) {
		// No row updated: a no-op on an existing deck, or the deck is absent /
		// tombstoned. Read back to tell them apart.
		d, err = r.GetByID(ctx, id, userID)
		if err == nil && d.DeletedAt != nil {
			return nil, ErrNotFound
		}
	}
	return d, err
}

// ChangedSince returns all decks (including soft-deleted) modified after the given time.
func (r *DeckRepo) ChangedSince(ctx context.Context, userID string, since time.Time) ([]*model.Deck, error) {
	rows, err := r.db.Query(ctx,
		`SELECT `+deckCols+` FROM decks WHERE user_id = $1 AND updated_at >= $2 ORDER BY updated_at`,
		userID, since,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return collectDecks(rows)
}

// SoftDelete tombstones the deck and cascades to its cards in one atomic
// statement (FK ON DELETE CASCADE never fires for soft deletes, so do it here).
func (r *DeckRepo) SoftDelete(ctx context.Context, id, userID string) error {
	now := time.Now().UTC()
	_, err := r.db.Exec(ctx, `
		WITH del_deck AS (
			UPDATE decks SET deleted_at = $1, updated_at = $1
			WHERE id = $2 AND user_id = $3 AND deleted_at IS NULL
		)
		UPDATE cards SET deleted_at = $1, updated_at = $1
		WHERE deck_id = $2 AND user_id = $3 AND deleted_at IS NULL`,
		now, id, userID,
	)
	return err
}

func collectDecks(rows pgx.Rows) ([]*model.Deck, error) {
	var out []*model.Deck
	for rows.Next() {
		d := &model.Deck{}
		if err := rows.Scan(&d.ID, &d.UserID, &d.Name, &d.Language, &d.Pinned, &d.CreatedAt, &d.UpdatedAt, &d.DeletedAt); err != nil {
			return nil, err
		}
		out = append(out, d)
	}
	return out, rows.Err()
}
