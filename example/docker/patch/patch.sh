#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SERVERS_DIR="/data/.fabric/server"
PROFILES_JSON="$SCRIPT_DIR/profiles.json"
OUT_DIR="$SCRIPT_DIR/out"

mkdir -p "$OUT_DIR"

# Find newest *-server.jar
SERVER_JAR="$(find "$SERVERS_DIR" -maxdepth 1 -type f -name "*-server.jar" \
    -printf "%T@ %p\n" | sort -nr | head -n1 | cut -d' ' -f2-)"

if [[ -z "${SERVER_JAR:-}" ]]; then
    echo "No server JAR found"
    rm -rf "$OUT_DIR"
    exit 1
fi

# Extract authlib
if AUTHLIB_ID=$(java -jar "$SCRIPT_DIR/server-extractor.jar" \
    "$SERVER_JAR" \
    "com.mojang:authlib" \
    "$OUT_DIR"); then

    echo "Found: $AUTHLIB_ID"

    AUTHLIB_JAR="$(find "$OUT_DIR" -maxdepth 1 -type f -name "authlib-*.jar" | head -n1)"

    if [[ -n "${AUTHLIB_JAR:-}" ]]; then
        echo "Authlib JAR: $AUTHLIB_JAR"

        if [[ "$AUTHLIB_JAR" == *_patched.jar ]]; then
            echo "Authlib JAR is already patched"
            rm -rf "$OUT_DIR"
            exit 0
        fi

        AUTHLIB_DIR="$(dirname "$AUTHLIB_JAR")"
        AUTHLIB_FILENAME="$(basename "$AUTHLIB_JAR")"
        AUTHLIB_BASENAME="${AUTHLIB_FILENAME%.jar}"

        AUTHLIB_PATCHED_JAR="$AUTHLIB_DIR/${AUTHLIB_BASENAME}_patched.jar"

        # Patch authlib
        if java -jar "$SCRIPT_DIR/authlib-patcher.jar" \
            "$AUTHLIB_JAR" \
            "$PROFILES_JSON"; then

            echo "Authlib patch successful ($AUTHLIB_PATCHED_JAR)"

            SERVER_DIR="$(dirname "$SERVER_JAR")"
            SERVER_FILENAME="$(basename "$SERVER_JAR")"
            SERVER_BASENAME="${SERVER_FILENAME%.jar}"

            SERVER_PATCHED_JAR="$SERVER_DIR/${SERVER_BASENAME}_patched.jar"

            # Patch server
            if java -jar "$SCRIPT_DIR/server-patcher.jar" \
                "$SERVER_JAR" \
                "$AUTHLIB_ID" \
                "$AUTHLIB_PATCHED_JAR"; then

                echo "Server patch successful ($SERVER_PATCHED_JAR)"

                rm -rf "$OUT_DIR"

                mv -f "$SERVER_PATCHED_JAR" "$SERVER_JAR"

                echo "Replaced original server JAR with patched version"
                exit 0
            else
                echo "Server patch failed"
                rm -rf "$OUT_DIR"
                exit 1
            fi
        else
            echo "Authlib patch failed"
            rm -rf "$OUT_DIR"
            exit 1
        fi
    else
        echo "Authlib JAR not found after extraction"
        rm -rf "$OUT_DIR"
        exit 1
    fi
else
    echo "Authlib extraction failed"
    rm -rf "$OUT_DIR"
    exit 1
fi
