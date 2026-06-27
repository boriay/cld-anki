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
// is closed automatically when the test finishes.
//
// Locally (no TEST_DATABASE_URL) it skips so `go test -tags=integration` works
// without a database; in CI a missing var is a hard failure so the suite can't
// silently turn green without actually running.
//
// All tests share one database and TRUNCATE on setup, so they MUST stay
// sequential — do not add t.Parallel() without per-test schemas/databases.
func newTestPool(t *testing.T) *pgxpool.Pool {
	t.Helper()

	dsn := os.Getenv("TEST_DATABASE_URL")
	if dsn == "" {
		if os.Getenv("CI") != "" {
			t.Fatal("TEST_DATABASE_URL must be set in CI")
		}
		t.Skip("TEST_DATABASE_URL not set; skipping integration test")
	}

	// Bound setup so a hung/unreachable DB fails fast instead of stalling until
	// the global `go test` timeout.
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	t.Cleanup(cancel)

	pool, err := pgxpool.New(ctx, dsn)
	if err != nil {
		t.Fatalf("connect to test db: %v", err)
	}
	t.Cleanup(pool.Close)

	if err := pool.Ping(ctx); err != nil {
		t.Fatalf("ping test db: %v", err)
	}

	// Safety net: setup TRUNCATEs cards/decks on every run, so refuse to touch
	// anything that isn't an obvious throwaway test database. current_database()
	// must equal the expected name (default "testdb" — what the Makefile, the
	// scripts/test-integration.sh container, and CI all use). A stray
	// TEST_DATABASE_URL pointing at prod then fails loudly here instead of
	// wiping it. Override the name with TEST_DB_NAME if you must.
	wantDB := os.Getenv("TEST_DB_NAME")
	if wantDB == "" {
		wantDB = "testdb"
	}
	var dbName string
	if err := pool.QueryRow(ctx, "SELECT current_database()").Scan(&dbName); err != nil {
		t.Fatalf("check test db name: %v", err)
	}
	if dbName != wantDB {
		t.Fatalf("refusing to run destructive integration tests against database %q "+
			"(expected %q). TEST_DATABASE_URL must point at a throwaway test DB; "+
			"set TEST_DB_NAME to override.", dbName, wantDB)
	}

	applyMigrations(ctx, t, pool)

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
func applyMigrations(ctx context.Context, t *testing.T, pool *pgxpool.Pool) {
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
	// Filename order — relies on zero-padded numeric prefixes (001, 002, ...).
	sort.Strings(files)

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
