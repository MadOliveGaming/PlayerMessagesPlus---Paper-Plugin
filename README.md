# PlayerMessages+

Paper plugin for Minecraft Java 26.3. It supports shared and personal join/leave/rejoin messages, reconnect-aware leave-message suppression, MiniMessage formatting controls, player bans from personal-message features, and data validation.

## Build

Requirements: JDK 25 and an internet connection for Gradle to download dependencies (and, if needed, the JDK toolchain).

- Windows: run `gradlew.bat build`
- Linux/macOS: run `./gradlew build`

The plugin JAR is written to `build/libs/PlayerMessagesPlus-1.0.0.jar`.

Install that JAR into the server's `plugins` folder and restart Paper. The plugin creates `plugins/PlayerMessagesPlus/` and its initial configuration/data files on first start.

## Commands

Run `/pmp help` in-game or from console. Administration commands require operator status.

## Data safety

The plugin does not automatically overwrite existing malformed JSON files. Reloads validate before applying data. `/pmp verify` scans the JSON files without reloading runtime data or editing files. Keep backups before manually editing data files.

## Notes

This is a source project, not a prebuilt plugin JAR. Paper 26.3 and Java 25 are the target. Build verification depends on access to a JDK 25 toolchain and the Paper Maven repository.


Naming note: Paper's technical plugin name is `PlayerMessagesPlus` because `+` is not allowed in `plugin.yml`. The public display/branding name remains `PlayerMessages+`.
