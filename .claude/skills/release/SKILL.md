---
name: release
description: Bump the CarGenome app version and prepare a release commit and tag. Run only when the user asks for a release.
disable-model-invocation: true
argument-hint: "[patch|minor|major or explicit X.Y.Z]"
---

# Release

1. `git status --short` must be clean apart from the intended changes; branch must be `main` unless the user says otherwise.
2. In `app/build.gradle.kts`: `versionCode` += 1, `versionName` per $ARGUMENTS (default: patch bump).
3. Run the `verify` skill steps 1–2 (tests + assembleDebug). Stop on failure.
4. Commit: `chore: bump version to X.Y.Z` (or fold into the feature commit if the user asks).
5. Show the user the commit and ask before `git tag vX.Y.Z` and `git push origin main --tags`.
   The `v*` tag triggers `.github/workflows/build.yml`, which builds, signs and publishes the release APK + `.sha256`
   used by the in-app updater (see `docs/UPDATE_DISTRIBUTION.md`).
