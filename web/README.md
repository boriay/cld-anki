# Cat Flashcards — Web

React + Vite + TypeScript single-page app. Online client that talks directly to
the existing Go backend REST API (`api.catflashcards.com`). Study your decks from
desktop or mobile browser; the same account (Firebase Auth) shares data with the
Android app.

## Stack

- **React 18 + Vite + TypeScript** — SPA, no SSR
- **Firebase Auth** (Web SDK) — email/password + Google (Apple planned)
- Direct REST calls to `/api/v1/*`; SM-2 review logic ported from the Android
  client (`src/domain/sm2.ts` ≡ `domain/SpacedRepetition.kt`)

## Setup

```bash
cd web
npm install
cp .env.example .env.local   # fill in VITE_FIREBASE_* + VITE_API_BASE_URL
npm run dev                  # http://localhost:5173
```

`.env.local` values:

- `VITE_API_BASE_URL` — `http://localhost:8080` for a local backend, or
  `https://api.catflashcards.com` to hit production.
- `VITE_FIREBASE_*` — from Firebase Console → Project `cld-anki-3505c` →
  Project settings → your Web app. The web config is a public client identifier,
  not a secret (access is gated server-side by ID-token verification).

## Firebase Console prerequisites (one-time, manual)

1. **Authentication → Sign-in method**: enable **Email/Password** and **Google**
   (add Apple later).
2. **Authentication → Settings → Authorized domains**: add `catflashcards.com`
   and `localhost`.
3. Register a **Web app** to get the `VITE_FIREBASE_*` values.

## Backend CORS

The backend allows the SPA origin via `CORS_ALLOWED_ORIGINS`
(default `https://catflashcards.com,http://localhost:5173`). Set it in the GKE
ConfigMap for production. Android sends no `Origin` header and is unaffected.

## Build & deploy

```bash
npm run build     # → dist/  (static assets)
```

Deploy target: GCS bucket served through the **existing** L7 LB with host-based
routing (`catflashcards.com` → bucket, `api.catflashcards.com` → backend). Note:
the k8s GCE Ingress can't target a GCS bucket directly — the bucket backend must
be attached to the LB's URL map outside the k8s Ingress (self-managed GCLB) so
the Ingress controller doesn't overwrite the routing rule.

## Known follow-ups

- **Android account migration**: Android currently uses Firebase *anonymous*
  auth, so its data lives under a per-device anonymous UID. To share decks with
  the web, link the anonymous account to a Google/email credential
  (`linkWithCredential`) so the UID persists, then sign into the same account on
  web. Until then, web is a separate (empty) account.
- **Apple Sign-In** — add the provider button in `src/screens/Login.tsx` once
  enabled in the console.
