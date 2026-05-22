# Docker example (authlib-patcher)

This folder contains a minimal Docker setup that runs a Minecraft server and
automatically patches Mojang authlib inside the server JAR. The patch injects
an allowlist of exception profiles (usernames + UUIDs) so those users can join
without disabling online-mode for everyone.

The setup uses the `itzg/minecraft-server` image with a small entrypoint
wrapper that:

- boots the server once to generate the server JAR (if missing)
- runs `patch/patch.sh` to patch authlib and embed it back into the server JAR
- restarts the server normally

## What is in this folder

- `compose.yaml` - Docker Compose service for a Fabric server
- `patch/entrypoint-wrapper.sh` - bootstraps the first server run and runs the patch
- `patch/patch.sh` - extracts authlib, patches it, and re-embeds it into the server
- `patch/profiles.json` - example exception profiles (edit this)
- `patch/*.jar` - built tools used by the patch scripts

The tools in `patch/` are built from this repository:

- `authlib-patcher.jar`
- `server-extractor.jar`
- `server-patcher.jar`

If you change the code, rebuild with `./gradlew build` and copy the updated JARs
from `build/libs/` into `example/docker/patch/`.

## How it works (high level)

1. The container starts with `entrypoint-wrapper.sh`.
2. If no server JAR exists yet, it starts the server once to generate it.
3. `patch.sh` locates the latest `*-server.jar` in `/data/.fabric/server`.
4. `server-extractor.jar` pulls out the `com.mojang:authlib` library.
5. `authlib-patcher.jar` patches that authlib using `profiles.json`.
6. `server-patcher.jar` embeds the patched authlib back into the server JAR.
7. The original server JAR is replaced with the patched one, then the server
  starts normally.

If the authlib JAR is already patched, `patch.sh` exits without changing it.

## Quick start

1. Edit `patch/profiles.json` with the usernames and UUIDs you want to allow.
2. From this folder, run:

```bash
docker compose up -d
```

3. Watch logs:

```bash
docker compose logs -f
```

The server data is stored in `./data` on the host, so worlds and configs
survive container restarts.

## Repatch after changing profiles

If you edit `patch/profiles.json` and want the server to be patched again, you
must delete the server JAR and restart the container so the patch step runs
fresh:

1. Stop the container: `docker compose down`
2. Delete the server JAR from the data volume.
  - By default it is in the `./data/.fabric/server/` directory on fabric
3. Start again: `docker compose up -d`

## Customize for your own server

- Update the server settings in `compose.yaml` (memory, version, MOTD, etc.).
- The example is configured for Fabric and expects the server JAR at
 `/data/.fabric/server`. If you use a different server type, update
 `SERVERS_DIR` in `patch/patch.sh` and `entrypoint-wrapper.sh`.
- You can replace the tools in `patch/` with newer builds from `build/libs/`.
