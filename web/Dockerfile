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
# nginx processes files in /etc/nginx/templates/*.template via envsubst at
# startup. NGINX_ENVSUBST_TEMPLATE_VARS limits substitution to only
# API_CSP_ORIGIN so nginx's own $uri / $request_uri etc. are left intact.
# Override API_CSP_ORIGIN at deploy time for staging/local without rebuilding.
ENV API_CSP_ORIGIN=https://api.catflashcards.com \
    NGINX_ENVSUBST_TEMPLATE_VARS=API_CSP_ORIGIN
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
