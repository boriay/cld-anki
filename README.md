# Cat Flashcards

A multi-platform flashcard app for learning Catalan, designed for Russian speakers.
Available as an **Android app** and at **[catflashcards.com](https://catflashcards.com)**.
Built with an Anki-style spaced repetition algorithm and a Go sync backend so your decks
follow you across devices.

> 🤖 **AI-generated project.** This codebase was primarily written with the assistance of
> AI tools — mainly Claude Code by Anthropic, with other tools used as needed. All code
> has been reviewed, tested, and integrated by the maintainer. See [NOTICE.md](NOTICE.md)
> for details.

## Features

- 📚 **Deck management** — create and manage multiple decks
- 🎓 **Spaced repetition** — SM-2 algorithm with Hard/Good/Easy grading, guaranteeing
  Hard < Good < Easy intervals at every review (Hard ×1.2, Easy ×1.3 modifiers)
- 📱 **Android app** — offline-first with Room; background sync to the server
- 🌐 **Web app** — online client at [catflashcards.com](https://catflashcards.com)
- 🔄 **Cross-device sync** — shared Firebase account keeps Android and web in sync
- ☁️ **Go backend** — REST API with Postgres, Firebase Auth ID-token verification
- 🌤️ **Weather background** — live weather from Open-Meteo drives the animated background

## Project structure

```
cld-anki/
├── android/          # Android app (Kotlin, Jetpack Compose, Room)
├── web/              # Web SPA (React 18, Vite, TypeScript) — see web/README.md
├── backend/          # Go sync backend (chi, pgx/v5, Firebase Admin)
├── shared/testdata/  # Cross-platform SM-2 golden fixtures (see below)
└── design/           # Design assets
```

## Getting started

### Android

Prerequisites: Android Studio Narwhal (2025.1.1+), JDK 21, Android SDK 37.

```bash
cd android
./gradlew installDebug     # build + install on connected device/emulator
./gradlew test             # unit tests
```

### Web

```bash
cd web
npm install
cp .env.example .env.local   # fill VITE_FIREBASE_* + VITE_API_BASE_URL
npm run dev                  # http://localhost:5173
npm test                     # parity + unit tests (no extra deps, uses node --test)
```

### Backend

```bash
cd backend
go build ./...
go test ./...                           # unit tests (no database needed)
make test-integration                   # integration tests against a throwaway Postgres
make test-integration ARGS="-v -run X"  # pass flags to go test
```

`make test-integration` runs [`scripts/test-integration.sh`](backend/scripts/test-integration.sh):
starts a `postgres:16-alpine` Docker container, applies migrations, runs
`go test -tags=integration ./...`, then removes the container.

## Architecture

### Android — offline-first MVVM

- **Room** stores decks and cards locally; every card has SM-2 fields
  (`interval`, `easeFactor`, `repetitions`, `nextReviewTime`).
- **SyncManager** runs background delta-sync (dual-cursor, soft-delete tombstones).
- **Firebase Auth** is optional — anonymous by default, linkable to Google/email
  so decks sync with the web account.

### Web — online SPA

React 18 + Vite SPA hosted as an nginx Deployment on GKE, served by the same L7 LB
as the backend. Firebase Auth (email/password + Google). Direct REST calls to the
backend; SM-2 logic is ported 1:1 from Android.

### Backend — Go REST API

`chi` router, `pgx/v5` Postgres pool, Firebase Admin SDK for ID-token verification.
Sync endpoint (`POST /api/v1/sync`) accepts a client delta and returns a server delta;
the SM-2 calculation is done entirely on the clients — the backend only stores and
validates the results.

Deployed on GKE (GCE Ingress, ManagedCertificate TLS):
- `https://api.catflashcards.com` — backend
- `https://catflashcards.com` — web static

## SM-2 algorithm

Standard SM-2 with three Anki-style extensions applied symmetrically on Android and web:

| Grade  | ease_factor change | interval formula            |
|--------|--------------------|-----------------------------|
| Again  | −0.54              | reset to 1 day, reps → 0   |
| Hard   | −0.14              | Good×1.2, capped at Good−1 |
| Good   | 0                  | interval × ease             |
| Easy   | +0.10              | Good×1.3, floored at Good+1 |

`reps=0` → interval 1; `reps=1` → interval 3; `reps≥2` → formula above.

**Cross-platform parity** is enforced by shared golden fixtures in
[`shared/testdata/`](shared/testdata/): the web parity suite (`npm test`) and the
Android `CrossPlatformParityTest` both assert against the same JSON files. Regenerate
after algorithm changes with `npm run test:gen` (from `web/`), then re-run both suites.

## Initial data

New accounts get a seed of **259 cards across 3 decks** (Catalan–Russian,
Catalan–Spanish, Catalan–English), covering greetings, numbers, verbs, nouns,
and common phrases. Generated from
[`backend/internal/seed/data.go`](backend/internal/seed/data.go) via
`POST /api/v1/seed` (idempotent; no-op if the account already has any decks).

## License

Licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file.

Copyright 2026 Boris Yusupov
