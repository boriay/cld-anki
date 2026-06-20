package db

import (
	"context"
	"errors"
	"testing"
	"time"
)

// fastRetry keeps the loop's timing tiny so tests don't sleep for real.
var fastRetry = retryConfig{
	maxElapsed:     500 * time.Millisecond,
	initialBackoff: time.Millisecond,
	maxBackoff:     5 * time.Millisecond,
	attemptTimeout: 50 * time.Millisecond,
}

func TestPingWithRetry_SucceedsAfterTransientFailures(t *testing.T) {
	calls := 0
	ping := func(context.Context) error {
		calls++
		if calls < 3 {
			return errors.New("connection refused")
		}
		return nil
	}

	if err := pingWithRetry(context.Background(), fastRetry, ping); err != nil {
		t.Fatalf("expected success once the DB comes up, got: %v", err)
	}
	if calls != 3 {
		t.Fatalf("expected 3 attempts (2 failures + 1 success), got %d", calls)
	}
}

func TestPingWithRetry_GivesUpAfterBudget(t *testing.T) {
	pingErr := errors.New("connection refused")
	calls := 0
	ping := func(context.Context) error {
		calls++
		return pingErr
	}

	err := pingWithRetry(context.Background(), fastRetry, ping)
	if err == nil {
		t.Fatal("expected an error when the DB never comes up")
	}
	// The original failure must be wrapped so callers can inspect it.
	if !errors.Is(err, pingErr) {
		t.Fatalf("expected wrapped ping error, got: %v", err)
	}
	if calls < 2 {
		t.Fatalf("expected multiple attempts before giving up, got %d", calls)
	}
}

func TestPingWithRetry_StopsOnContextCancel(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	calls := 0
	ping := func(context.Context) error {
		calls++
		cancel() // cancel mid-flight; the loop should bail at the next wait
		return errors.New("connection refused")
	}

	err := pingWithRetry(ctx, fastRetry, ping)
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("expected context.Canceled, got: %v", err)
	}
	if calls != 1 {
		t.Fatalf("expected exactly 1 attempt before cancellation took effect, got %d", calls)
	}
}
