#!/bin/bash
set -euo pipefail

# --- Local development bootstrap ---
# This entrypoint generates dev-only runtime secrets on first boot into a
# persistent volume mounted at /run/cwk-secrets, then exports them to the
# Spring Boot process. Subsequent restarts reuse the same generated values
# so encrypted data remains decryptable.
#
# This is NOT the production secret-management path. To override either
# value, set CIVICWORKS_ENCRYPTION_KEY or SPRING_DATASOURCE_PASSWORD in the
# environment (or supply *_FILE pointing at a mounted secret) before
# launching the container — the entrypoint will not regenerate when those
# are provided.

SECRETS_DIR="/run/cwk-secrets"
mkdir -p "$SECRETS_DIR"

generate_random_b64() {
    # 32 random bytes, URL-safe base64 (no padding).
    head -c 32 /dev/urandom | base64 | tr -d '\n='
}

# Encryption key: prefer env, then *_FILE override, then generate.
if [ -z "${CIVICWORKS_ENCRYPTION_KEY:-}" ]; then
    if [ -n "${CIVICWORKS_ENCRYPTION_KEY_FILE:-}" ] && [ -f "$CIVICWORKS_ENCRYPTION_KEY_FILE" ]; then
        export CIVICWORKS_ENCRYPTION_KEY="$(cat "$CIVICWORKS_ENCRYPTION_KEY_FILE")"
    else
        KEY_FILE="$SECRETS_DIR/encryption_key"
        if [ ! -s "$KEY_FILE" ]; then
            umask 077
            generate_random_b64 > "$KEY_FILE"
            echo "[entrypoint] generated dev encryption key at $KEY_FILE"
        fi
        export CIVICWORKS_ENCRYPTION_KEY="$(cat "$KEY_FILE")"
    fi
fi

# DB password: only honour explicit env or file. Otherwise leave blank;
# the dev compose stack runs postgres on the internal network with
# trust auth, so no credential is required there.
if [ -n "${SPRING_DATASOURCE_PASSWORD_FILE:-}" ] && [ -f "$SPRING_DATASOURCE_PASSWORD_FILE" ]; then
    export SPRING_DATASOURCE_PASSWORD="$(cat "$SPRING_DATASOURCE_PASSWORD_FILE")"
fi

exec java -jar /app/app.jar "$@"
