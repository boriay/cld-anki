//go:build integration

// Integration tests run against a real Postgres (sync's SQL relies on
// Postgres-specific behaviour — e.g. parameter type inference — that mocks and
// SQLite can't reproduce). Build-tagged so a plain `go test ./...` without a
// database stays green; CI runs `go test -tags=integration ./...` with a
// postgres service container (see .github/workflows/ci.yml).
package repository

import (
	"context"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
)

// newTestPool connects to the database from TEST_DATABASE_URL, applies the
// project migrations, truncates the tables, and returns a ready pool. The pool
// is closed automatically when the test finishes. Skips (not fails) when the
// env var is unset so the suite can be run locally without a database.
func newTestPool(t *testing.T) *pgxpool.Pool {
	t.Helper()

	dsn := os.Getenv("TEST_DATABASE_URL")
	if dsn == "" {
		t.Skip("TEST_DATABASE_URL not set; skipping integration test")
	}

	ctx := context.Background()
	pool, err := pgxpool.New(ctx, dsn)
	if err != nil {
		t.Fatalf("connect to test db: %v", err)
	}
	t.Cleanup(pool.Close)

	if err := pool.Ping(ctx); err != nil {
		t.Fatalf("ping test db: %v", err)
	}

	applyMigrations(t, pool)

	// Clean slate for each test. RESTART IDENTITY is harmless (no serials) but
	// CASCADE keeps this working if FKs change.
	if _, err := pool.Exec(ctx, `TRUNCATE cards, decks RESTART IDENTITY CASCADE`); err != nil {
		t.Fatalf("truncate: %v", err)
	}

	return pool
}

// applyMigrations runs every *.up.sql in migrations/ in filename order. Our
// migrations are idempotent (IF NOT EXISTS / ADD COLUMN IF NOT EXISTS), so
// running them before each test is safe and also doubles as a migration smoke
// test.
func applyMigrations(t *testing.T, pool *pgxpool.Pool) {
	t.Helper()

	// repository package lives at backend/internal/repository.
	migDir := filepath.Join("..", "..", "migrations")
	entries, err := os.ReadDir(migDir)
	if err != nil {
		t.Fatalf("read migrations dir: %v", err)
	}

	var files []string
	for _, e := range entries {
		if name := e.Name(); !e.IsDir() && strings.HasSuffix(name, ".up.sql") {
			files = append(files, name)
		}
	}
	sort.Strings(files)

	ctx := context.Background()
	for _, f := range files {
		sql, err := os.ReadFile(filepath.Join(migDir, f))
		if err != nil {
			t.Fatalf("read migration %s: %v", f, err)
		}
		if _, err := pool.Exec(ctx, string(sql)); err != nil {
			t.Fatalf("apply migration %s: %v", f, err)
		}
	}
}

// ptr returns a pointer to t, for the *time.Time tombstone fields.
func ptr(t time.Time) *time.Time { return &t }
