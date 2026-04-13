#!/bin/bash
set -euo pipefail

# --- CivicWorks DB Bootstrap (optional) ---
# `docker compose up --build` already initializes the database via Flyway
# migrations on first boot. Use this script only when you need to reset or
# restore the database state.
#
# Volume safety policy:
#   * pgdata       : Postgres data files                  — RESET removes this
#   * pgbackups    : on-volume pg_dump backup files       — preserved by RESET
#   * cwk-secrets  : per-installation encryption key      — preserved by RESET
#
# Removing cwk-secrets would silently break decryption of any restored backup,
# so RESET keeps it. Use `./init_db.sh purge` to wipe everything (you will
# also lose the ability to decrypt prior backups).
#
# Usage:
#   ./init_db.sh                 # safe reset: drops only pgdata
#   ./init_db.sh reset           # alias for the default safe reset
#   ./init_db.sh restore FILE    # restore from a backup file (key preserved)
#   ./init_db.sh purge           # full wipe, including encryption key + backups
#                                #   (you will not be able to decrypt prior backups)
#   ./init_db.sh status          # print current volume state

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

ACTION="${1:-reset}"

project_volume() {
    # Compose prefixes named volumes with the project name (defaults to the
    # directory name). Resolve relative to THIS project only so we never
    # mistake a different project's volume for one of ours.
    local short="$1"
    local project
    project="$(docker compose config --format json 2>/dev/null \
        | python3 -c 'import sys,json; print(json.load(sys.stdin).get("name",""))' 2>/dev/null \
        || basename "$SCRIPT_DIR")"
    [ -z "$project" ] && project="$(basename "$SCRIPT_DIR")"
    docker volume ls --format '{{.Name}}' | awk -v p="${project}_${short}" '$0==p {print; exit}'
}

case "$ACTION" in
    reset|init)
        echo "Stopping containers and removing only the pgdata volume..."
        docker compose down --remove-orphans
        PGDATA_VOL="$(project_volume pgdata)"
        if [ -n "$PGDATA_VOL" ]; then
            docker volume rm -f "$PGDATA_VOL" >/dev/null
            echo "Removed Postgres data volume: $PGDATA_VOL"
        else
            echo "No pgdata volume present (nothing to remove)."
        fi
        echo "Preserved: cwk-secrets (encryption key), pgbackups (backup files)."
        echo "Done. The next 'docker compose up --build' will recreate the DB and run Flyway."
        ;;

    purge)
        echo "WARNING: 'purge' will delete ALL named volumes:"
        echo "         - pgdata     (database files)"
        echo "         - pgbackups  (backup files)"
        echo "         - cwk-secrets (encryption key needed to decrypt prior backups)"
        echo "After purge, any encrypted data in older backups becomes UNRECOVERABLE."
        read -r -p "Type 'purge' to confirm: " CONFIRM
        if [ "$CONFIRM" != "purge" ]; then
            echo "Aborted."
            exit 1
        fi
        docker compose down -v --remove-orphans
        echo "Purge complete. Next 'docker compose up --build' will start with a fresh key + empty DB."
        ;;

    restore)
        BACKUP_FILE="${2:?Usage: ./init_db.sh restore <backup-file>}"
        if [ ! -f "$BACKUP_FILE" ]; then
            echo "Error: Backup file not found: $BACKUP_FILE" >&2
            exit 1
        fi
        echo "Restore preserves the cwk-secrets volume so encrypted columns in"
        echo "the backup remain decryptable. If this backup was taken under a"
        echo "DIFFERENT encryption key than the current cwk-secrets/encryption_key,"
        echo "encrypted columns will not decrypt — re-key before restoring."
        docker compose up -d db
        until docker compose exec db pg_isready -U civicworks -q 2>/dev/null; do
            sleep 1
        done
        echo "Dropping and recreating database schema..."
        docker compose exec db psql -U civicworks -d civicworks \
            -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
        echo "Restoring from backup..."
        docker compose exec -T db pg_restore -U civicworks -d civicworks < "$BACKUP_FILE" \
            || docker compose exec -T db psql -U civicworks -d civicworks < "$BACKUP_FILE"
        echo "Restore complete. Encryption key was preserved — encrypted columns should decrypt."
        ;;

    status)
        echo "Compose project state:"
        docker compose ps --format 'table {{.Name}}\t{{.Status}}\t{{.Ports}}' || true
        echo
        echo "Project volumes (kept across 'reset'):"
        for short in pgdata pgbackups cwk-secrets; do
            VOL="$(project_volume "$short")"
            if [ -n "$VOL" ]; then
                echo "  $short -> $VOL (present)"
            else
                echo "  $short -> (absent)"
            fi
        done
        ;;

    *)
        echo "Usage: ./init_db.sh [reset|restore <file>|purge|status]" >&2
        exit 1
        ;;
esac
