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

const deckCols = `id, user_id, name, description, created_at, updated_at, deleted_at`

func scanDeck(row pgx.Row) (*model.Deck, error) {
	d := &model.Deck{}
	err := row.Scan(&d.ID, &d.UserID, &d.Name, &d.Description, &d.CreatedAt, &d.UpdatedAt, &d.DeletedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, ErrNotFound
	}
	return d, err
}

// Upsert inserts or updates a deck. Server record wins if its updated_at is newer.
func (r *DeckRepo) Upsert(ctx context.Context, d *model.Deck) error {
	_, err := r.db.Exec(ctx, `
		INSERT INTO decks (`+deckCols+`)
		VALUES ($1, $2, $3, $4, $5, $6, $7)
		ON CONFLICT (id) DO UPDATE SET
			name        = EXCLUDED.name,
			description = EXCLUDED.description,
			updated_at  = EXCLUDED.updated_at,
			deleted_at  = EXCLUDED.deleted_at
		WHERE decks.user_id = EXCLUDED.user_id
		  AND decks.updated_at < EXCLUDED.updated_at`,
		d.ID, d.UserID, d.Name, d.Description, d.CreatedAt, d.UpdatedAt, d.DeletedAt,
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
		if err := rows.Scan(&d.ID, &d.UserID, &d.Name, &d.Description, &d.CreatedAt, &d.UpdatedAt, &d.DeletedAt); err != nil {
			return nil, err
		}
		out = append(out, d)
	}
	return out, rows.Err()
}
