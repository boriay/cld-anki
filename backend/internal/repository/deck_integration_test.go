//go:build integration

package repository

import (
	"context"
	"testing"
)

// Regression for the 42P08 ("could not determine data type of parameter $3")
// 500 on PUT /api/v1/decks/:id: when only pin=true is sent, name is nil and
// pgx sends an untyped NULL that Postgres can't classify inside the
// `$3 IS NOT NULL` predicate. Before the ::text cast this failed.
func TestDeckUpdateFields_PinOnlyNilName(t *testing.T) {
	pool := newTestPool(t)
	ctx := context.Background()
	decks := NewDeckRepo(pool)

	if err := decks.Upsert(ctx, newDeck("d1")); err != nil {
		t.Fatalf("deck upsert: %v", err)
	}

	// name == nil, pin == true — the exact path that triggers 42P08.
	got, err := decks.UpdateFields(ctx, "d1", testUser, nil, true)
	if err != nil {
		t.Fatalf("UpdateFields(nil name, pin=true) 42P08 regression: %v", err)
	}
	if !got.Pinned {
		t.Errorf("Pinned = false, want true after pin-only update")
	}
	if got.Name != "Deck d1" {
		t.Errorf("Name = %q, want %q (must not be overwritten)", got.Name, "Deck d1")
	}
}

// Renaming without pinning must work (name non-nil, pin=false).
func TestDeckUpdateFields_RenameOnly(t *testing.T) {
	pool := newTestPool(t)
	ctx := context.Background()
	decks := NewDeckRepo(pool)

	if err := decks.Upsert(ctx, newDeck("d1")); err != nil {
		t.Fatalf("deck upsert: %v", err)
	}

	name := "Renamed"
	got, err := decks.UpdateFields(ctx, "d1", testUser, &name, false)
	if err != nil {
		t.Fatalf("UpdateFields(rename, pin=false): %v", err)
	}
	if got.Name != "Renamed" {
		t.Errorf("Name = %q, want %q", got.Name, "Renamed")
	}
}

// No-op (same name, pin already set) must return the current row without error.
func TestDeckUpdateFields_NoOpReturnsCurrentRow(t *testing.T) {
	pool := newTestPool(t)
	ctx := context.Background()
	decks := NewDeckRepo(pool)

	d := newDeck("d1")
	if err := decks.Upsert(ctx, d); err != nil {
		t.Fatalf("deck upsert: %v", err)
	}

	name := d.Name
	got, err := decks.UpdateFields(ctx, "d1", testUser, &name, false)
	if err != nil {
		t.Fatalf("UpdateFields no-op: %v", err)
	}
	if got.Name != d.Name {
		t.Errorf("Name = %q, want %q", got.Name, d.Name)
	}
}
