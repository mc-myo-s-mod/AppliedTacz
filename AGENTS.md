# Repository Guidelines

## Project Structure & Module Organization
This is a single-module NeoForge mod for Minecraft 1.21.1. Java sources live in `src/main/java/me/myogoo/appliedtacz`, with feature areas split by package: `registry/` for deferred registers, `block/` and `block/blcokentity/` for blocks and block entities, `menu/` for container menus, `client/` for client hooks and renderers, `mixin/` for Sponge Mixin integrations, and `util/` for shared helpers.

Resources live in `src/main/resources`. Keep mod metadata in `META-INF/mods.toml`, mixin config in `appliedtacz.mixins.json`, localized strings in `assets/appliedtacz/lang`, models and blockstates in `assets/appliedtacz`, and recipes, loot tables, and tags under `data/`. Generated data belongs in `src/generated/resources`. Test sources go in `src/test/java` and `src/test/resources`.

## Build, Test, and Development Commands
Use the Gradle wrapper from the repository root through Windows `cmd.exe`, which matches this workspace's configured Java/Gradle environment:

- `cmd.exe /C gradlew.bat compileJava` compiles the mod against NeoForge and catches Java errors early.
- `cmd.exe /C gradlew.bat build` creates the jar and runs the standard verification lifecycle.
- `cmd.exe /C gradlew.bat runClient` launches a local modded client for manual testing.
- `cmd.exe /C gradlew.bat runServer` starts a local dedicated server.
- `cmd.exe /C gradlew.bat runData` regenerates data-driven assets into `src/generated/resources`.
- `cmd.exe /C gradlew.bat runGameTestServer` runs NeoForge GameTests.
- `cmd.exe /C gradlew.bat test` is reserved for `src/test` tests; the tree is present but currently empty.

## Coding Style & Naming Conventions
Follow the existing Java style: 4-space indentation, UTF-8 source files, and one top-level class per file. Keep package names lowercase (`me.myogoo.appliedtacz`) and use PascalCase for classes such as `AETaCZBlock` or `AEGunSmithTableMenu`. Use lowercase snake_case for resource ids and JSON file names such as `gun_smith_table.json`. No formatter or linter is configured, so match the surrounding code before submitting.

## Testing Guidelines
There is no established unit-test suite yet. Prefer small Forge GameTests for gameplay behavior and add focused `*Test` classes under `src/test/java` only when the required dependencies are introduced. For UI, menu, or block logic changes, include manual verification steps with the exact command used, usually `./gradlew runClient`.

## Commit & Pull Request Guidelines
This repository currently has no committed history, so no message convention is established yet. Use short imperative commits with a type prefix, for example `feat: add ammo workbench menu sync` or `fix: preserve block owner on placement`.

Pull requests should describe the gameplay or integration change, list verification steps, and call out any resource or data updates. Include screenshots when changing rendered blocks, screens, or localization-visible UI. Avoid committing local IDE files, `run/` contents, or crash logs.

## RULES
- Find code first in /mnt/f/IntelliJ/fork and /mnt/f/IntelliJ
- run gradlew, use cmd.exe