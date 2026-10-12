#!/usr/bin/env bash
#
# Restores rows of selected tables from a full pg_dump archive (-Fc) into a freshly migrated database.
# Only data is restored: the tables must already exist (created by Flyway on API start) and be empty.
# Columns are matched by name, so the dump may come from an older schema version as long as
# every dumped column still exists. A table missing from the dump is skipped (0 rows in the summary).
#
# Runs on the deployment machine, next to .env (PIXPLAZE_DB, PIXPLAZE_DB_USERNAME).
# Everything is loaded in one transaction: on any error nothing is written.
# Stop the API first so no one signs up during the restore: docker compose stop api
#
# Usage:
#   ./restore-tables.sh full-2026-10-12.dump
#

set -euo pipefail

# Parents before children: foreign keys are checked row by row.
# The role reference table itself is not restored: Flyway fills it (R__insert_values_role.sql).
tables=(profile profile_signup profile_role minecraft_player minecraft_player_profile)
db_container="${DB_CONTAINER:-pixplaze-db}"

dump="${1:?Usage: $0 <dump file>}"

set -a; . ./.env; set +a
psql=(docker exec -i "$db_container" psql -U "$PIXPLAZE_DB_USERNAME" -d "$PIXPLAZE_DB" -v ON_ERROR_STOP=1 -X -q)
archive="/tmp/restore-$$.dump"

docker cp "$dump" "$db_container:$archive"
trap 'docker exec "$db_container" rm -f "$archive"' EXIT

echo "Restoring: ${tables[*]}"
for table in "${tables[@]}"; do
    docker exec "$db_container" pg_restore --data-only --table="$table" -f - "$archive"
done | "${psql[@]}" --single-transaction > /dev/null

# Identity counters are not part of table data: move them past the restored ids
"${psql[@]}" -c "
    SELECT setval(seq, (SELECT coalesce(max(id), 0) + 1 FROM public.profile), false)
    FROM pg_get_serial_sequence('public.profile', 'id') AS seq;" > /dev/null

for table in "${tables[@]}"; do
    echo "  $table: $("${psql[@]}" -tA -c "SELECT count(*) FROM public.$table") rows"
done
