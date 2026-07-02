#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT="${1:-$ROOT_DIR/kingnest_backup.sql}"
SOURCE_URL="${DATABASE_URL:-postgresql://postgres:postgres@localhost:5432/kingnest}"

echo "Exporting database from: $SOURCE_URL"
echo "Output file: $OUTPUT"

pg_dump "$SOURCE_URL" \
  --no-owner \
  --no-privileges \
  --format=plain \
  --file="$OUTPUT"

echo "Done. Import on cloud with:"
echo "  psql \"\$DATABASE_URL\" -f kingnest_backup.sql"
