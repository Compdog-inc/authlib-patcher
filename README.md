# authlib-patcher

Simple tools for patching Mojang authlib and embedding the patched library
into a server jar.

## What it does

The patch adds a list of exception profiles that can join without authentication.
This lets those usernames join without turning off online-mode for the whole
server and risking security.

## Build

```bash
./gradlew build
```

Artifacts:

- `build/libs/authlib-patcher.jar`
- `build/libs/server-patcher.jar`

## Authlib patcher

Command line arguments:

```bash
java -jar authlib-patcher.jar <input.jar> <profiles.json>
```

- `<input.jar>`: original authlib jar.
- `<profiles.json>`: list of exception profiles.
- Output: `<input>_patched.jar` in the same folder.

Example:

```bash
java -jar build/libs/authlib-patcher.jar authlib.jar profiles.json
```

## Server patcher

Command line arguments:

```bash
java -jar server-patcher.jar <server.jar> <group:artifact:version> <artifact.jar>
```

- `<server.jar>`: server jar to patch.
- `<group:artifact:version>`: library id as listed in `META-INF/libraries.list`.
- `<artifact.jar>`: jar to embed (usually the patched authlib jar).
- Output: `<server>_patched.jar` in the same folder.

Example:

```bash
java -jar build/libs/server-patcher.jar server.jar com.mojang:authlib:3.11.50 authlib_patched.jar
```

## profiles.json

Format: JSON array of objects with `username` and `uuid`.

Example:

```json
[
  {
    "username": "Alice",
    "uuid": "123e4567-e89b-12d3-a456-426614174000"
  },
  {
    "username": "Bob",
    "uuid": "987e6543-e21b-45d3-b654-123456789abc"
  }
]
```
