# Releasing AppliedTaCZ

Publishing is driven by an existing GitHub Release. The release workflow builds one Minecraft version, attaches its
release JAR to GitHub, and publishes the same file to Modrinth and CurseForge.

## Repository setup

Configure these GitHub Actions repository variables:

- `MODRINTH_PROJECT_ID`: `PtRJTvUr`
- `CURSEFORGE_PROJECT_ID`: `1511859`

Configure these GitHub Actions repository or organization secrets:

- `MODRINTH_TOKEN`: a Modrinth personal access token that can create versions for Applied TaCZ.
- `CURSEFORGE_TOKEN`: a CurseForge API token that can upload files for Applied TaCZ.

The workflow uses the built-in `GITHUB_TOKEN` to attach the JAR to the existing GitHub Release.

The workflow must exist on the repository's default branch. The multi-version repository uses `master` as that branch.

## Release tags

Use one of these tag formats:

- `v1.20.1-<mod-version>` builds `forge-1.20.1` with Java 17 and publishes it as a Forge file.
- `v1.21.1-<mod-version>` builds `neoforge-1.21.1` with Java 21 and publishes it as a NeoForge file.

For example, `v1.20.1-15.0.5` produces `AppliedTaCZ-1.20.1-15.0.5.jar`. The tag must point to a commit on `master`.
The version from the tag is passed to Gradle as `-Pmod_version`, so the produced metadata and file name match the
release even when backfilling an existing tag.

Stable GitHub Releases publish as `release`; GitHub pre-releases publish as `beta`.

## Automatic release

### 1.20.1-15.1.0 / 1.21.1-19.1.0 preparation

These versions include the normal and Extended TaCZ Molecular Assemblers and the TaCZ Pattern Encoding
Terminal on both loaders. Both target official TaCZ 1.2.0; the official NeoForge dependency is currently
marked beta. The assembler exclusion below applies only to the historical 19.0.2 compatibility patch.

Before publishing, compile, run data generation, build, and run the GameTests for each module. Verify the
packaged JARs with MCT in isolated copies of the target modpacks, including actual pattern encoding and
crafting, upgrades, offline behavior, rendering, and save/reload. Check the deployed artifact hashes and
ensure only one AppliedTaCZ JAR and one matching TaCZ JAR are installed per instance.

Local instance deployment does not authorize pushing commits, creating tags, or publishing releases.

### Historical 1.21.1-19.0.2 preparation

This is a workbench compatibility patch for official TaCZ 1.2.0 on NeoForge. Do not include the uncommitted
TaCZ Molecular Assembler work, Extended assemblers, or their related assets in the release commit or JAR.
Build from the clean release checkout, not the working directory containing those unreleased sources.

Before publishing:

- Run `cmd.exe /c "gradlew.bat :neoforge-1.21.1:compileJava :neoforge-1.21.1:build --console=plain"`.
- Smoke-test the client and dedicated server with official TaCZ 1.2.0.
- In an ME ammo workbench, hold a Glock 17, turn off the held-item filter, select 12g ammunition, request a
  craftable missing ingredient, and return from the AE2 crafting screen. Confirm the same recipe, tab, recipe
  page, mouse position, and ME ingredient counts are restored. Repeat with a matching recipe and an empty hand.
- Check that a normal TaCZ workbench and table-specific recipe restrictions still work.
- Inspect the release JAR for unintended assembler classes, models, blockstates, recipes, and language entries.
- Publish only the `1.21.1-19.0.2` changelog section. The historical entries below it describe earlier releases.

The intended tag is `v1.21.1-19.0.2`. Creating or publishing the GitHub Release must wait for explicit approval.

### Publishing steps

1. Update and verify the target module.
2. Tag the release commit with the matching version tag.
3. Create a GitHub Release for that tag and publish it.
4. The `Publish AppliedTaCZ Release` workflow builds the selected module, attaches the JAR, then publishes it to
   Modrinth and CurseForge.

## Backfill an existing release

Open **Actions > Publish AppliedTaCZ Release > Run workflow** on `master` and enter an existing release tag. Manual
backfills use the existing GitHub Release body and overwrite the GitHub Release asset with the rebuilt JAR. Modrinth
and CurseForge may reject a version that has already been published, so only backfill missing platform versions.
