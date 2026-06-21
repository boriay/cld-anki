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

The SPA is baked into an nginx image (`Dockerfile`, `nginx.conf` with SPA
fallback) and served by the in-cluster `web-static` Deployment/Service (`k8s/`)
behind the **existing** GCE Ingress, routed by host (`catflashcards.com` →
web-static, `api.catflashcards.com` → backend). GCE Ingress can only target
Services, not a GCS bucket, so the static site runs as a Service on the same L7
LB. The apex has its own `ManagedCertificate` (`cld-anki-cert-web`), leaving the
api cert untouched.

Deploy: build with the `VITE_*` build args, push to Artifact Registry, then
`kubectl set image deployment/web-static nginx=<image>:<tag> -n cld-anki`.

## Known follow-ups

- **Android account login** (done): Android is anonymous by default and the
  account screen links the anonymous session to a Google/email credential
  (`linkWithCredential`, falling back to sign-in on collision) so decks sync with
  the web. Switching to a different account clears the local store and pulls the
  account's data to avoid mixing UIDs.
- **Apple Sign-In** — add the provider button in `src/screens/Login.tsx` once
  enabled in the console.
