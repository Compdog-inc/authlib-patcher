#!/bin/bash
set -e

PATCH_SCRIPT=/patch/patch.sh
SERVERS_DIR="/data/.fabric/server"

find_server_jar() {
    find "$SERVERS_DIR" -maxdepth 1 -type f -name "*-server.jar" 2>/dev/null \
        -printf "%T@ %p\n" 2>/dev/null | sort -nr | head -n1 | cut -d' ' -f2-
}

chmod +x "$PATCH_SCRIPT"

SERVER_JAR="$(find_server_jar || true)"

if [[ -z "${SERVER_JAR:-}" ]]; then
    echo "No server JAR found, bootstrapping server..."

    /start &
    MC_PID=$!

    echo "Waiting for server extraction..."

    while true; do
        SERVER_JAR="$(find_server_jar || true)"

        if [[ -n "${SERVER_JAR:-}" ]]; then
            break
        fi

        sleep 2
    done

    echo "Server extracted:"
    echo "$SERVER_JAR"

    echo "Force killing bootstrap server..."
    pkill -9 -f "java" || true

    wait "$MC_PID" 2>/dev/null || true
fi

echo "Running patch script..."
"$PATCH_SCRIPT"

echo "Starting Minecraft server..."
exec /start
