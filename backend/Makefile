PROJECT_ID ?= cld-anki
IMAGE      := europe-west1-docker.pkg.dev/$(PROJECT_ID)/cld-anki/cld-anki-backend
TAG        ?= $(shell git rev-parse --short HEAD)

.PHONY: build run test test-integration lint docker push deploy migrate-up migrate-down

build:
	go build -o bin/server ./cmd/server

run:
	go run ./cmd/server

test:
	go test ./...

# Spin up a throwaway Postgres, run the integration tests, tear it down.
# Pass extra `go test` args via ARGS, e.g. make test-integration ARGS="-v".
test-integration:
	./scripts/test-integration.sh $(ARGS)

lint:
	golangci-lint run ./...

docker:
	docker build -t $(IMAGE):$(TAG) -t $(IMAGE):latest .

push: docker
	docker push $(IMAGE):$(TAG)
	docker push $(IMAGE):latest

deploy: push
	kubectl set image deployment/cld-anki-backend app=$(IMAGE):$(TAG) -n cld-anki

# Apply every *.up.sql in ascending order; *.down.sql in descending order.
# Migrations are written to be idempotent (IF [NOT] EXISTS) so re-runs are safe.
migrate-up:
	@for f in $$(ls migrations/*.up.sql | sort); do \
		echo "applying $$f"; psql "$(DATABASE_URL)" -v ON_ERROR_STOP=1 -f "$$f" || exit 1; \
	done

migrate-down:
	@for f in $$(ls migrations/*.down.sql | sort -r); do \
		echo "reverting $$f"; psql "$(DATABASE_URL)" -v ON_ERROR_STOP=1 -f "$$f" || exit 1; \
	done
