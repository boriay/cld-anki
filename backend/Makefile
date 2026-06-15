PROJECT_ID ?= cld-anki
IMAGE      := europe-west1-docker.pkg.dev/$(PROJECT_ID)/cld-anki/cld-anki-backend
TAG        ?= $(shell git rev-parse --short HEAD)

.PHONY: build run test lint docker push deploy migrate-up migrate-down

build:
	go build -o bin/server ./cmd/server

run:
	go run ./cmd/server

test:
	go test ./...

lint:
	golangci-lint run ./...

docker:
	docker build -t $(IMAGE):$(TAG) -t $(IMAGE):latest .

push: docker
	docker push $(IMAGE):$(TAG)
	docker push $(IMAGE):latest

deploy: push
	kubectl set image deployment/cld-anki-backend app=$(IMAGE):$(TAG) -n cld-anki

migrate-up:
	psql "$(DATABASE_URL)" -f migrations/001_initial.up.sql

migrate-down:
	psql "$(DATABASE_URL)" -f migrations/001_initial.down.sql
