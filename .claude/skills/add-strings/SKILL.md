---
name: add-strings
description: Add or rename UI strings in CarGenome across all four locales (en, ru, de, es). Use whenever UI text is introduced or changed.
---

# Localized strings

Files (keep them in the same key order):
- `app/src/main/res/values/strings.xml` — English (source)
- `app/src/main/res/values-ru/strings.xml`
- `app/src/main/res/values-de/strings.xml`
- `app/src/main/res/values-es/strings.xml`

Rules:
- Key: `snake_case`, prefixed by screen/feature (`fuel_`, `settings_`, `loyalty_`...). Grep for an existing key before adding one.
- Do not Read whole files (~580 strings each). Grep the neighbouring key, then Edit right after it in all four files.
- Escape `'` as `\'`; use `%1$s`/`%1$d` positional args; plurals go in `<plurals>`.
- Theme "system" is translated «Системная» (`settings_theme_system`).
- In Compose use `stringResource(R.string.key)`; in ViewModels pass resource ids, not resolved text.

Check parity after editing:
```
grep -c '<string ' app/src/main/res/values*/strings.xml
```
All four counts must match (English may lead only by `translatable="false"` entries).
