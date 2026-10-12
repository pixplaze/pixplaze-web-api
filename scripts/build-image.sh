#!/usr/bin/env bash
#
# Builds the jar and the Docker image, then packs everything needed for deployment
# into target/dist. The version is taken from <version> in pom.xml.
# If Docker is not reachable at /var/run/docker.sock, runs scripts/link-docker-socket.sh first.
#
# Output (target/dist):
#   pixplaze-web-api-<version>.tar.gz   image archive for 'docker load'
#   version.env                         PIXPLAZE_TAG=<version> for docker compose
#   docker-compose.yml                  copy of the compose file
#
# Environment:
#   PLATFORM   target image platform (default: linux/amd64)
#
# Usage:
#   scripts/build-image.sh
#

set -euo pipefail

cd "$(dirname "$0")/.."

platform="${PLATFORM:-linux/amd64}"
image="pixplaze-web-api"
dist_dir="target/dist"

# Testcontainers (jOOQ codegen) needs Docker at the default socket path.
docker_socket_live() {
    [[ -S /var/run/docker.sock ]] || return 1
    if command -v curl > /dev/null; then
        curl --silent --fail --max-time 3 --unix-socket /var/run/docker.sock http://localhost/_ping > /dev/null
    fi
}

if ! docker_socket_live; then
    echo "Docker is not available at /var/run/docker.sock, running scripts/link-docker-socket.sh"
    scripts/link-docker-socket.sh
fi

version="$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout)"
echo "Building $image:$version for $platform"

./mvnw -DskipTests package

docker build --platform "$platform" \
    --label org.opencontainers.image.version="$version" \
    -t "$image:$version" --load .

rm -rf "$dist_dir"
mkdir -p "$dist_dir"
docker save "$image:$version" | gzip > "$dist_dir/$image-$version.tar.gz"
echo "PIXPLAZE_TAG=$version" > "$dist_dir/version.env"
cp docker-compose.yml "$dist_dir/"

echo
echo "Done: $dist_dir"
ls -1 "$dist_dir"
