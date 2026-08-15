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

1. Update and verify the target module.
2. Tag the release commit with the matching version tag.
3. Create a GitHub Release for that tag and publish it.
4. The `Publish AppliedTaCZ Release` workflow builds the selected module, attaches the JAR, then publishes it to
   Modrinth and CurseForge.

## Backfill an existing release

Open **Actions > Publish AppliedTaCZ Release > Run workflow** on `master` and enter an existing release tag. Manual
backfills use the existing GitHub Release body and overwrite the GitHub Release asset with the rebuilt JAR. Modrinth
and CurseForge may reject a version that has already been published, so only backfill missing platform versions.
