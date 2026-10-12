#!/usr/bin/env bash
#
# Makes the Docker socket available at the default path /var/run/docker.sock,
# so Testcontainers (jOOQ codegen, tests) finds Docker without DOCKER_HOST and
# TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE.
#
# If /var/run/docker.sock already points to a live socket, does nothing.
# Otherwise looks for the socket of the active docker context and of known
# runtimes (colima, Docker Desktop, OrbStack, Rancher Desktop) and creates a
# symlink to the first live one. Needs sudo when /var/run is not writable.
#
# /var/run may be cleared on reboot - just run the script again.
# Not needed on Windows: Docker Desktop exposes the named pipe Testcontainers uses.
#
# Usage:
#   scripts/link-docker-socket.sh
#

set -euo pipefail

default_socket="/var/run/docker.sock"

case "$(uname -s)" in
    Darwin|Linux) : ;;
    *)  echo "Unsupported OS '$(uname -s)': nothing to do (Docker Desktop on Windows works as is)" >&2
        exit 1 ;;
esac

# Socket file exists and the daemon answers on it.
is_live() {
    local socket="$1"
    [[ -S "$socket" ]] || return 1
    if command -v curl > /dev/null; then
        curl --silent --fail --max-time 3 --unix-socket "$socket" http://localhost/_ping > /dev/null
    fi
}

if is_live "$default_socket"; then
    echo "$default_socket is live$( [[ -L "$default_socket" ]] && echo " (-> $(readlink "$default_socket"))" ): nothing to do"
    exit 0
fi

candidates=()

# Active docker context is the most precise hint.
if command -v docker > /dev/null; then
    context_host="$(docker context inspect --format '{{.Endpoints.docker.Host}}' 2> /dev/null || true)"
    if [[ "$context_host" == unix://* ]]; then
        candidates+=("${context_host#unix://}")
    fi
fi

candidates+=(
    "${COLIMA_HOME:-$HOME/.colima}/${COLIMA_PROFILE:-default}/docker.sock"  # colima
    "$HOME/.docker/run/docker.sock"                                         # Docker Desktop (macOS)
    "$HOME/.docker/desktop/docker.sock"                                     # Docker Desktop (Linux)
    "$HOME/.orbstack/run/docker.sock"                                       # OrbStack
    "$HOME/.rd/docker.sock"                                                 # Rancher Desktop
)

found=""
for socket in "${candidates[@]}"; do
    if [[ "$socket" != "$default_socket" ]] && is_live "$socket"; then
        found="$socket"
        break
    fi
done

if [[ -z "$found" ]]; then
    echo "No live Docker socket found. Checked:" >&2
    printf '  %s\n' "${candidates[@]}" >&2
    echo "Start Docker (e.g. 'colima start' or Docker Desktop) and run the script again." >&2
    exit 1
fi

sudo_cmd=()
if [[ ! -w "$(dirname "$default_socket")" ]]; then
    sudo_cmd=(sudo)
    echo "Creating $default_socket requires sudo"
fi

"${sudo_cmd[@]+"${sudo_cmd[@]}"}" ln -sf "$found" "$default_socket"
echo "Linked $default_socket -> $found"

if is_live "$default_socket"; then
    echo "Docker is reachable at $default_socket"
else
    echo "Link created, but Docker does not answer at $default_socket" >&2
    exit 1
fi
