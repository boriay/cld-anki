#!/usr/bin/env bash
# Spin up a throwaway Postgres, run the build-tagged integration tests against
# it, then tear it down — all in one command. Mirrors the postgres:16 service
# container used by the backend-tests CI job.
#
# Usage:
#   ./scripts/test-integration.sh            # run the whole repository suite
#   ./scripts/test-integration.sh -run TestCardUpsert_LiveCardNilDeletedAt -v
#   PG_PORT=5544 KEEP_DB=1 ./scripts/test-integration.sh   # custom port, keep DB
#
# Extra args are passed straight through to `go test`.
set -euo pipefail

CONTAINER="${CONTAINER:-cldanki-test-pg}"
PG_PORT="${PG_PORT:-5544}"
PG_IMAGE="${PG_IMAGE:-postgres:16-alpine}"
KEEP_DB="${KEEP_DB:-0}"  # set to 1 to leave the container running after tests

# Run from the backend/ dir regardless of where the script was invoked.
cd "$(dirname "$0")/.."

cleanup() {
	if [ "$KEEP_DB" != "1" ]; then
		docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
	fi
}
trap cleanup EXIT

# Remove a stale container from a previous interrupted run, then start fresh.
docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
echo ">> starting $PG_IMAGE on host port $PG_PORT"
docker run -d --name "$CONTAINER" \
	-e POSTGRES_USER=testuser -e POSTGRES_PASSWORD=testpass -e POSTGRES_DB=testdb \
	-p "$PG_PORT:5432" "$PG_IMAGE" >/dev/null

echo ">> waiting for Postgres to accept connections"
# Probe over TCP (-h 127.0.0.1), not the unix socket: the official image runs a
# temporary socket-only server during initdb, so a socket-based pg_isready can
# report ready before the real TCP listener is up — leading to "connection
# reset" once we connect from the host. A successful SELECT 1 over TCP means the
# final server is actually serving.
for i in $(seq 1 60); do
	if docker exec "$CONTAINER" psql -h 127.0.0.1 -U testuser -d testdb -c 'SELECT 1' >/dev/null 2>&1; then
		break
	fi
	if [ "$i" -eq 60 ]; then
		echo "!! Postgres did not become ready in time" >&2
		exit 1
	fi
	sleep 1
done

export TEST_DATABASE_URL="postgres://testuser:testpass@localhost:$PG_PORT/testdb?sslmode=disable"
echo ">> running integration tests"
go test -tags=integration ./... "$@"
