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

touch "$file"

# Pre-fill the migration with an SQL template inferred from the name.
# The detection is based on the slug prefix:
#   create_table_<table>        -> CREATE TABLE <table> (...)
#   create_index_<idx>[_on_<t>] -> CREATE INDEX <idx> ON <t> (...)
#   drop_table_<table>          -> DROP TABLE <table>
#   alter_table_<table>         -> ALTER TABLE <table> ...
#   add_column_<col>[_to_<t>]   -> ALTER TABLE <t> ADD COLUMN <col> ...
#   insert_values[_into]_<t>    -> INSERT INTO <t> (...) VALUES (...)
# Anything else leaves the file empty.
template=""
case "$slug" in
    create_table_*)
        table="${slug#create_table_}"
        template="CREATE TABLE ${table} (
);"
        ;;
    create_index_*)
        rest="${slug#create_index_}"
        if [[ "$rest" == *_on_* ]]; then
            index_name="${rest%%_on_*}"
            table="${rest#*_on_}"
        else
            index_name="$rest"
            table="<table_name>"
        fi
        template="CREATE INDEX ${index_name} ON ${table} ();"
        ;;
    drop_table_*)
        table="${slug#drop_table_}"
        template="DROP TABLE ${table};"
        ;;
    alter_table_*)
        table="${slug#alter_table_}"
        template="ALTER TABLE ${table};"
        ;;
    add_column_*)
        rest="${slug#add_column_}"
        if [[ "$rest" == *_to_* ]]; then
            column="${rest%%_to_*}"
            table="${rest#*_to_}"
        else
            column="$rest"
            table="<table_name>"
        fi
        template="ALTER TABLE ${table} ADD COLUMN ${column};"
        ;;
    insert_values_into_*)
        table="${slug#insert_values_into_}"
        template="INSERT INTO ${table} () VALUES ();"
        ;;
    insert_values_*)
        table="${slug#insert_values_}"
        template="INSERT INTO ${table} () VALUES ();"
        ;;
esac

if [[ -n "$template" ]]; then
    printf '%s\n' "$template" > "$file"
fi

echo "$file"
