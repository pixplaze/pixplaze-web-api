#!/usr/bin/env bash
set -euo pipefail

# Генерирует ES256 (P-256) ключевую пару для подписи токенов и печатает значения
# Uncomment on macOS openssl=LibreSSL (for compatibility):
# export PATH="$(brew --prefix openssl@3)/bin:$PATH"

openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out priv.pem
openssl pkey -in priv.pem -pubout -out pub.pem

PRIV_B64=$(openssl pkey -in priv.pem -outform DER | base64 | tr -d '\n')
PUB_B64=$(openssl pkey -pubin -in pub.pem -outform DER | base64 | tr -d '\n')

echo
echo "EC_PRIVATE_KEY=${PRIV_B64}"
echo "EC_PUBLIC_KEY=${PUB_B64}"
