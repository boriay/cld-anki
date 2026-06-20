package db

import (
	"context"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
)

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
	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		return nil, fmt.Errorf("create pool: %w", err)
	}
	if err := pool.Ping(ctx); err != nil {
		return nil, fmt.Errorf("ping db: %w", err)
	}
	return pool, nil
}
