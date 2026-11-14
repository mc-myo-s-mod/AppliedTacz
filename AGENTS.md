# Repository Guidelines

## Project Structure & Module Organization
This is a multi-loader mod with `forge-1.20.1` (Forge, Java 17), `neoforge-1.21.1` (NeoForge, Java 21), and shared sources in `common`. Each source tree uses `src/main/java/me/myogoo/appliedtacz`, with feature areas split by package: `init/` or `registry/` for registrations, `block/` and `block/blcokentity/` for blocks and block entities, `menu/` for container menus, `client/` for client hooks and renderers, `mixin/` for Sponge Mixin integrations, and `util/` for shared helpers.

Shared resources live in `common/src/main/resources`; maintain both versions' localized strings there under `assets/appliedtacz/lang`. Loader-specific resources live in each loader's `src/main/resources` and override the same paths from `common`. Keep loader metadata in `META-INF`, mixin config in `appliedtacz.mixins.json`, models and blockstates in `assets/appliedtacz`, and recipes, loot tables, and tags under `data/`. Generated data belongs in each loader's `src/generated/resources`. Test sources go in the corresponding module's `src/test/java` and `src/test/resources`.

## Build, Test, and Development Commands
Use the Gradle wrapper from the repository root through Windows `cmd.exe`, which matches this workspace's configured Java/Gradle environment. Select each affected loader explicitly; the examples below use NeoForge, and the same task names are available under `:forge-1.20.1`:

- `cmd.exe /C "gradlew.bat :neoforge-1.21.1:compileJava --console=plain"` compiles the mod against NeoForge and catches Java errors early.
- `cmd.exe /C "gradlew.bat :neoforge-1.21.1:build --console=plain"` creates the jar and runs the standard verification lifecycle.
- `cmd.exe /C "gradlew.bat :neoforge-1.21.1:runClient --console=plain"` launches a local modded client for manual testing.
- `cmd.exe /C "gradlew.bat :neoforge-1.21.1:runServer --console=plain"` starts a local dedicated server.
- `cmd.exe /C "gradlew.bat :neoforge-1.21.1:runData --console=plain"` regenerates data-driven assets into the loader's `src/generated/resources`.
- `cmd.exe /C "gradlew.bat :neoforge-1.21.1:runGameTestServer --console=plain"` runs NeoForge GameTests.
- `cmd.exe /C "gradlew.bat :neoforge-1.21.1:test --console=plain"` runs configured `src/test` tests; a `NO-SOURCE` result is not a passing test suite.

## Coding Style & Naming Conventions
Follow the existing Java style: 4-space indentation, UTF-8 source files, and one top-level class per file. Keep package names lowercase (`me.myogoo.appliedtacz`) and use PascalCase for classes such as `AETaCZBlock` or `AEGunSmithTableMenu`. Use lowercase snake_case for resource ids and JSON file names such as `gun_smith_table.json`. No formatter or linter is configured, so match the surrounding code before submitting.

## Testing Guidelines
Both loader modules contain GameTests under the main `gametest` package. Extend the relevant loader tests for gameplay behavior, and run both suites for shared changes. Add focused `*Test` classes under `src/test/java` only when the required dependencies are introduced. For UI, menu, or block logic changes, also verify the affected client behavior and record the exact runtime and command used.

## Commit & Pull Request Guidelines
Follow the existing history's short imperative commits with a type prefix, for example `feat: add ammo workbench menu sync` or `fix: preserve block owner on placement`.

Pull requests should describe the gameplay or integration change, list verification steps, and call out any resource or data updates. Include screenshots when changing rendered blocks, screens, or localization-visible UI. Avoid committing local IDE files, `run/` contents, or crash logs.

## RULES
- Find code first in /mnt/f/IntelliJ/fork and /mnt/f/IntelliJ
- run gradlew, use cmd.exe
