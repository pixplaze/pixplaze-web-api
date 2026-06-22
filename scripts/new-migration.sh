#!/usr/bin/env bash
#
# Creates empty file of Flyway-migration with version timestamp prefix.
#
# Usage:
#   scripts/new-migration.sh "<name>" [domain]
#
# Examples:
#   scripts/new-migration.sh create_table_shop minecraft
#   scripts/new-migration.sh "add index to profile email" account
#

set -euo pipefail

migration_root="src/main/resources/db/migration"

if [[ $# -lt 1 || $# -gt 3 ]]; then
    echo "Usage: $0 <name> [dir]" >&2
    exit 1
fi

name="$1"
target="${2:-}"

if [[ -z "$target" ]]; then
    target_dir="$migration_root"
elif [[ -f "$target" ]]; then
    target_dir="$(dirname "$target")"
elif [[ "$target" = /* ]]; then
    target_dir="$target"
else
    target_dir="$migration_root/$target"
fi

case "$target_dir" in
    *"$migration_root"*) : ;;
    *)  echo "warning: '$target_dir' is outside of $migration_root - creating in migrations root directory" >&2
        target_dir="$migration_root" ;;
esac

slug="${name// /_}"
timestamp="$(date +%Y%m%d%H%M%S)"

mkdir -p "$target_dir"

file="$target_dir/V${timestamp}__${slug}.sql"

if [[ -e "$file" ]]; then
    echo "File already exists: $file" >&2
    exit 1
fi

echo "$file"
