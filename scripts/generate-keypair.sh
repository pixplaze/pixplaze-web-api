#!/usr/bin/env bash

set -euo pipefail
umask 077

OUTPUT="${1:-.env}"

#if [[ -e "$OUTPUT" ]]; then
#    echo "File already exists: $OUTPUT" >&2
#    exit 1
#fi

TMP_DIR=$(mktemp -d)

trap 'rm -rf "$TMP_DIR";' EXIT

generate_ec_pair() {
    local name="$1"
    local key_file="$TMP_DIR/${name}.pem"

    openssl genpkey \
        -algorithm EC \
        -pkeyopt ec_paramgen_curve:prime256v1 \
        -out "$key_file"

    local private_key
    local public_key

    # Private: PKCS#8 DER -> Base64
    private_key=$(
        openssl pkcs8 \
            -topk8 \
            -nocrypt \
            -in "$key_file" \
            -outform DER |
        openssl base64 -A
    )

    # Public: X.509 SPKI DER -> Base64
    public_key=$(
        openssl pkey \
            -in "$key_file" \
            -pubout \
            -outform DER |
        openssl base64 -A
    )

    printf '%s_PRIVATE_KEY=%s\n' "$name" "$private_key"
    printf '%s_PUBLIC_KEY=%s\n' "$name" "$public_key"
}

{
    echo "# JWT keys (EC P-256)"
    generate_ec_pair JWT

    echo
    echo "# VAPID keys (EC P-256)"
    generate_ec_pair VAPID
}
