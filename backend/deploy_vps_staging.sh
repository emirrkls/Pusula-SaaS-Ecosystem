#!/bin/bash
# VPS deployment helper
# Run this on your VPS to start backend safely with required secrets

set -e  # Exit on any error

echo "Starting Pusula VPS deployment..."

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DB_NAME="pusula_db"
DB_USER="postgres"
DB_PASSWORD="${DB_PASSWORD:-}"
JWT_SECRET="${JWT_SECRET:-}"
GOOGLE_WEB_CLIENT_ID="${GOOGLE_WEB_CLIENT_ID:-}"
APPLY_APP_STORE_MIGRATION="${APPLY_APP_STORE_MIGRATION:-false}"
APP_STORE_MIGRATION_FILE="${APP_STORE_MIGRATION_FILE:-$SCRIPT_DIR/src/main/resources/V7__app_store_subscription_verification.sql}"
DB_BACKUP_DIR="${DB_BACKUP_DIR:-/var/backups/pusula}"

if [ -z "$DB_PASSWORD" ]; then
  echo "ERROR: DB_PASSWORD is required."
  echo "Set it before running: export DB_PASSWORD='...'"
  exit 1
fi

if [ -z "$JWT_SECRET" ]; then
  echo "ERROR: JWT_SECRET is required."
  echo "Set it before running: export JWT_SECRET='...'"
  exit 1
fi

echo "Step 1/4: Verifying database..."
if ! sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'" | grep -q 1; then
  sudo -u postgres createdb "$DB_NAME"
fi
sudo -u postgres psql -c "ALTER USER $DB_USER PASSWORD '$DB_PASSWORD';"

echo "Step 2/4: Verifying database tables..."
sudo -u postgres psql -d $DB_NAME -c "\dt"

echo "Step 3/4: Checking optional App Store migration..."
if [ "$APPLY_APP_STORE_MIGRATION" = "true" ]; then
  if [ ! -f "$APP_STORE_MIGRATION_FILE" ]; then
    echo "ERROR: Migration file not found: $APP_STORE_MIGRATION_FILE"
    exit 1
  fi
  mkdir -p "$DB_BACKUP_DIR"
  BACKUP_FILE="$DB_BACKUP_DIR/${DB_NAME}_before_app_store_$(date -u +%Y%m%dT%H%M%SZ).dump"
  echo "Creating database backup: $BACKUP_FILE"
  sudo -u postgres pg_dump -Fc "$DB_NAME" > "$BACKUP_FILE"
  test -s "$BACKUP_FILE"
  echo "Applying idempotent App Store migration..."
  sudo -u postgres psql -v ON_ERROR_STOP=1 -d "$DB_NAME" -f "$APP_STORE_MIGRATION_FILE"
else
  echo "App Store migration skipped. Set APPLY_APP_STORE_MIGRATION=true after reviewing V7 and backup location."
fi

echo "Step 4/4: Starting backend on port 8080..."
DB_PASSWORD="$DB_PASSWORD" JWT_SECRET="$JWT_SECRET" GOOGLE_WEB_CLIENT_ID="$GOOGLE_WEB_CLIENT_ID" \
  java -jar /root/app.jar --spring.profiles.active=vps
