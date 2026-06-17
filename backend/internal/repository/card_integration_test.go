//go:build integration

package repository

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/boriay/cld-anki/backend/internal/model"
)

const testUser = "user-1"

func newDeck(id string) *model.Deck {
	now := time.Now().UTC()
	return &model.Deck{
		ID:        id,
		UserID:    testUser,
		Name:      "Deck " + id,
		CreatedAt: now,
		UpdatedAt: now,
	}
}

func newCard(id, deckID string) *model.Card {
	now := time.Now().UTC()
	return &model.Card{
		ID:             id,
		DeckID:         deckID,
		UserID:         testUser,
		Front:          "front",
		Back:           "back",
		Interval:       1,
		EaseFactor:     2.5,
		NextReviewTime: now,
		CreatedAt:      now,
		UpdatedAt:      now,
	}
}

// Regression for the 42P08 ("could not determine data type of parameter $12")
// 500 on POST /api/v1/sync: a live (non-tombstone) card has DeletedAt == nil,
// so pgx sends an untyped NULL that Postgres can't classify inside the
// `$12 IS NOT NULL` subquery. Before the ::timestamptz cast this failed.
func TestCardUpsert_LiveCardNilDeletedAt(t *testing.T) {
	pool := newTestPool(t)
	ctx := context.Background()
	decks := NewDeckRepo(pool)
	cards := NewCardRepo(pool)

	if err := decks.Upsert(ctx, newDeck("d1")); err != nil {
		t.Fatalf("deck upsert: %v", err)
	}

	c := newCard("c1", "d1") // DeletedAt == nil
	if err := cards.Upsert(ctx, c); err != nil {
		t.Fatalf("live card upsert (the 42P08 regression): %v", err)
	}

	got, err := cards.GetByID(ctx, "c1", testUser)
	if err != nil {
		t.Fatalf("get card: %v", err)
	}
	if got.DeletedAt != nil {
		t.Errorf("DeletedAt = %v, want nil", got.DeletedAt)
	}
}

// Tombstone path: a deleted card must be writable even under a soft-deleted
// deck (cascade-delete sync). Here DeletedAt != nil, so the param type is
// inferred from the value — this path always worked, but pin it down.
func TestCardUpsert_TombstoneUnderDeletedDeck(t *testing.T) {
	pool := newTestPool(t)
	ctx := context.Background()
	decks := NewDeckRepo(pool)
	cards := NewCardRepo(pool)

	if err := decks.Upsert(ctx, newDeck("d1")); err != nil {
		t.Fatalf("deck upsert: %v", err)
	}
	// Soft-delete the deck (cascades the existing card too, but we test a fresh
	// tombstone card arriving afterwards).
	if err := decks.SoftDelete(ctx, "d1", testUser); err != nil {
		t.Fatalf("deck soft delete: %v", err)
	}

	c := newCard("c1", "d1")
	c.DeletedAt = ptr(time.Now().UTC())
	if err := cards.Upsert(ctx, c); err != nil {
		t.Fatalf("tombstone card upsert under deleted deck: %v", err)
	}

	got, err := cards.GetByID(ctx, "c1", testUser)
	if err != nil {
		t.Fatalf("get card: %v", err)
	}
	if got.DeletedAt == nil {
		t.Error("DeletedAt = nil, want tombstone")
	}
}

// An orphan write (live card with no existing/owned parent deck) writes no row
// and must surface as ErrNotFound so the batch can drop it.
func TestCardUpsert_OrphanReturnsNotFound(t *testing.T) {
	pool := newTestPool(t)
	ctx := context.Background()
	cards := NewCardRepo(pool)

	err := cards.Upsert(ctx, newCard("c1", "missing-deck"))
	if !errors.Is(err, ErrNotFound) {
		t.Fatalf("orphan upsert err = %v, want ErrNotFound", err)
	}
}

// Last-write-wins: an older update against a newer server row is a silent
// no-op (not an error) and must not overwrite the newer fields.
func TestCardUpsert_StaleUpdateIsNoOp(t *testing.T) {
	pool := newTestPool(t)
	ctx := context.Background()
	decks := NewDeckRepo(pool)
	cards := NewCardRepo(pool)

	if err := decks.Upsert(ctx, newDeck("d1")); err != nil {
		t.Fatalf("deck upsert: %v", err)
	}

	base := newCard("c1", "d1")
	base.UpdatedAt = time.Now().UTC()
	base.Front = "newer"
	if err := cards.Upsert(ctx, base); err != nil {
		t.Fatalf("base upsert: %v", err)
	}

	stale := newCard("c1", "d1")
	stale.UpdatedAt = base.UpdatedAt.Add(-time.Hour)
	stale.Front = "older"
	if err := cards.Upsert(ctx, stale); err != nil {
		t.Fatalf("stale upsert should be a no-op, got: %v", err)
	}

	got, err := cards.GetByID(ctx, "c1", testUser)
	if err != nil {
		t.Fatalf("get card: %v", err)
	}
	if got.Front != "newer" {
		t.Errorf("Front = %q, want %q (stale write must not win)", got.Front, "newer")
	}
}
