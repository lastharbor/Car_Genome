# CarGenome

Android car-maintenance log: fuel, odometer, service schedule, expenses, VIN decoding, loyalty cards.
Reply to the user in Russian. Code, comments and commit messages in English.

## Stack
Kotlin 2.4 · AGP 9.4 · Compose (BOM 2026.09) · Room 2.8 · Hilt · DataStore · WorkManager · Coroutines/Flow.
compileSdk 37, targetSdk 36, minSdk 26. JDK = Android Studio JBR (system Oracle JDK is rejected by AGP).

## Layout
- `core/vin` — pure Kotlin/JVM VIN decoding, no Android APIs.
- `app/.../data` — Room (entities, dao, `CarGenomeDatabase` + manual `MIGRATION_x_y`), repositories, network, sync, update.
- `app/.../domain` — pure logic (fuel, analytics, maintenance scheduling, premium licence, updates).
- `app/.../ui` — Compose screens + ViewModels, type-safe navigation routes.
- `server/` + `data/sync/` — cloud sync, a far-future feature: not deployed, not reachable from the UI. Don't prioritise it.
- `tools/` — Python scripts that build the WMI dataset.
- Deep docs (local only, read on demand): `docs/wiki/01..06_*.md`, `docs/UPDATE_DISTRIBUTION.md`, Obsidian notes in `vault/`.

## Commands (always via the filtered runner — raw `gradlew` is blocked by a hook)
```
python scripts/ai/gradle.py :core:vin:test
python scripts/ai/gradle.py :app:testDebugUnitTest
python scripts/ai/gradle.py :app:testDebugUnitTest --tests "*FuelConsumptionTest"
python scripts/ai/gradle.py :app:assembleDebug
python scripts/ai/gradle.py :app:lintDebug
```
Full logs land in `.ai-logs/`; grep them instead of rerunning with more output. Test totals are printed per task, so passing tests are visible too.
Run the narrowest task that proves the change (one module, one test class) before the full suite.

## Tools
- `context7` MCP: check current Compose/Room/Hilt/AGP APIs before writing code against them (stack versions are newer than training data).
- `mobile` MCP: drive the device; read the screen with `mobile_list_elements_on_screen` (text, cheap), screenshot only for visual checks.
- Kotlin/Python LSP plugins: prefer go-to-definition/references over grepping across the codebase.
- Subagents: `build-runner` for builds/tests, `android-reviewer` before commits. Skills: `/verify`, `/room-migration`, `/add-strings`, `/release`.

## Hard rules
- `domain.*` has no `android.*`/`androidx.*` imports; pure functions, JVM-tested.
- Single build, no Free/Premium flavors. `IS_PREMIUM` is `false`; premium is unlocked at runtime by an ECDSA-signed code (`PremiumManager`), exposed to UI via `LocalIsPremium`.
- Vehicle context comes only from the typed route (`savedStateHandle.toRoute<XRoute>().vehicleId`). No vehicle-switcher dropdowns in inner screens' TopAppBar.
- IO on `Dispatchers.IO`, heavy math in `withContext(Dispatchers.Default)`, never `GlobalScope`. Use the injected dispatchers from `di/Dispatchers.kt`.
- Compose: state hoisting, `collectAsStateWithLifecycle()`, no heavy work in composable bodies (`remember`/`derivedStateOf`).
- Immutable `data class` UI state, `val` and read-only collections.
- No hardcoded UI strings. Every new string goes to all four: `values/`, `values-ru/`, `values-de/`, `values-es/` `strings.xml`.
- Money is stored as integer minor units.
- Room schema change ⇒ bump `CarGenomeDatabase.VERSION`, add `MIGRATION_N_N+1`, commit the exported `app/schemas/.../N+1.json`.

## Done means
1. Relevant unit tests pass, then `:app:testDebugUnitTest` and `:app:assembleDebug` pass.
2. UI change + device attached (`adb devices`): install, screenshot, check with `uiautomator dump` + `python tools/ui_nodes.py`.
3. No temp files left (screens, dumps, logs are gitignored but don't create new kinds).
4. Conventional Commits (`feat:`, `fix:`, `refactor:`, `chore:`, `docs:`). Releases bump `versionCode`+`versionName` in `app/build.gradle.kts`; a `v*` tag triggers the CI release.

## Do not touch / read
APKs, `*.tar`, keystores, `tools/keys/`, `tools/keygen.py`, user backups (`databases/`, `files/`, `CarGenome_Restore/`, `cargenome_backup*.json`).
`wmi.json` / `vpic_wmi_cache.json` are 0.4–0.5 MB: query them with a Python one-liner, never read whole.
`ui/analytics/AnalyticsScreen.kt` is ~80 KB: read by line ranges after grepping.
