# Template — DO NOT commit with real values.
# Apply with: kubectl apply -f secret.yaml  (after filling in values)
#
# DATABASE_URL connects through Cloud SQL Auth Proxy sidecar on 127.0.0.1:5432.
# Use IAM database authentication (recommended) or a Cloud SQL user password.

apiVersion: v1
kind: Secret
metadata:
  name: cld-anki-secret
  namespace: cld-anki
type: Opaque
stringData:
  DATABASE_URL: "postgresql://DB_USER:DB_PASS@127.0.0.1:5432/flashcard"
