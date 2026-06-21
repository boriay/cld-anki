# Multi-stage: build the Vite SPA, then serve the static bundle from nginx.
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Vite inlines VITE_* at build time. These are public client config (the
# Firebase web apiKey is a client id, not a secret), passed as build args so
# nothing environment-specific is committed.
ARG VITE_API_BASE_URL
ARG VITE_FIREBASE_API_KEY
ARG VITE_FIREBASE_AUTH_DOMAIN
ARG VITE_FIREBASE_PROJECT_ID
ARG VITE_FIREBASE_APP_ID
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_FIREBASE_API_KEY=$VITE_FIREBASE_API_KEY \
    VITE_FIREBASE_AUTH_DOMAIN=$VITE_FIREBASE_AUTH_DOMAIN \
    VITE_FIREBASE_PROJECT_ID=$VITE_FIREBASE_PROJECT_ID \
    VITE_FIREBASE_APP_ID=$VITE_FIREBASE_APP_ID
# Fail fast: missing build args would otherwise bake "undefined" into the bundle
# and ship a runtime-broken SPA (no API base, no Firebase config).
RUN for v in VITE_API_BASE_URL VITE_FIREBASE_API_KEY VITE_FIREBASE_AUTH_DOMAIN \
        VITE_FIREBASE_PROJECT_ID VITE_FIREBASE_APP_ID; do \
        eval "val=\$$v"; \
        if [ -z "$val" ]; then echo "ERROR: build arg $v is required" >&2; exit 1; fi; \
    done
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
