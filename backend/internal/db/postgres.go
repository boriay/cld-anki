package db

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
)

// retryConfig controls the startup ping retry loop. Production values live in
// defaultRetry; tests inject tiny durations so they don't sleep for real.
type retryConfig struct {
	maxElapsed     time.Duration // total budget before giving up
	initialBackoff time.Duration // first wait between attempts
	maxBackoff     time.Duration // cap for the exponential backoff
	attemptTimeout time.Duration // per-ping timeout
}

var defaultRetry = retryConfig{
	maxElapsed:     60 * time.Second,
	initialBackoff: 200 * time.Millisecond,
	maxBackoff:     3 * time.Second,
	attemptTimeout: 5 * time.Second,
}

func NewPool(ctx context.Context, dsn string) (*pgxpool.Pool, error) {
	cfg, err := pgxpool.ParseConfig(dsn)
	if err != nil {
		return nil, fmt.Errorf("parse db config: %w", err)
	}
	// Cloud SQL db-f1-micro caps max_connections at 25 (3 reserved for
	// superuser → ~22 usable). During a zero-downtime deploy two pods overlap
	// (maxSurge=1), so the per-pod ceiling must satisfy 2*MaxConns + system
	// connections <= 22. Keep it at 8 (2*8+system ≈ 20) to avoid
	// "too many clients" on rollout. Raising the DB flag is unsafe on f1-micro
	// (≈0.6 GB RAM, risks OOM).
	cfg.MaxConns = 8
	// Keep a warm connection ready so the first sync after a long idle period
	// doesn't pay for a cold connect to Cloud SQL f1-micro (which can blow past
	// the client's timeout). The background health check keeps these alive
	// instead of letting MaxConnIdleTime reap them.
	cfg.MinConns = 2
	cfg.MaxConnIdleTime = time.Hour
	cfg.HealthCheckPeriod = time.Minute
	// NewWithConfig is lazy — it doesn't dial until the first query/ping, so the
	// retry loop below is what actually waits for the DB (or the cloud-sql-proxy
	// sidecar) to become reachable.
	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		return nil, fmt.Errorf("create pool: %w", err)
	}
	if err := pingWithRetry(ctx, defaultRetry, pool.Ping); err != nil {
		pool.Close()
		return nil, err
	}
	return pool, nil
}

// pingWithRetry pings until it succeeds, the elapsed budget runs out, or ctx is
// cancelled. This tolerates a not-yet-ready DB or proxy at startup (the common
// case: the cloud-sql-proxy sidecar isn't listening yet) instead of exiting and
// crash-looping the pod. Backoff is exponential, capped at maxBackoff.
func pingWithRetry(ctx context.Context, rc retryConfig, ping func(context.Context) error) error {
	deadline := time.Now().Add(rc.maxElapsed)
	backoff := rc.initialBackoff
	var lastErr error

	for attempt := 1; ; attempt++ {
		attemptCtx, cancel := context.WithTimeout(ctx, rc.attemptTimeout)
		err := ping(attemptCtx)
		cancel()
		if err == nil {
			return nil
		}
		lastErr = err

		// Stop if the next backoff would overrun the budget — avoids a final
		// sleep we'd only abandon.
		if time.Now().Add(backoff).After(deadline) {
			return fmt.Errorf("ping db: giving up after %s: %w", rc.maxElapsed, lastErr)
		}
		slog.Default().Warn("ping db failed, retrying", "attempt", attempt, "backoff", backoff, "err", err)

		// NewTimer (not time.After) so we can Stop it on cancellation instead of
		// leaving a live timer dangling until it fires.
		timer := time.NewTimer(backoff)
		select {
		case <-ctx.Done():
			timer.Stop()
			return fmt.Errorf("ping db: %w", ctx.Err())
		case <-timer.C:
		}
		if backoff *= 2; backoff > rc.maxBackoff {
			backoff = rc.maxBackoff
		}
	}
}
