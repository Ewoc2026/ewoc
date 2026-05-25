# Localization Guide

This project uses a source-first localization flow for Android string resources.

## Goal

- Keep `app/src/main/res/values/strings.xml` as the single source of truth.
- Maintain locale files (for example `app/src/main/res/values-fi/strings.xml`) without key drift.
- Prevent runtime regressions caused by placeholder mismatch.

## Permanent Rules

1. Add or edit user-facing strings in `values/strings.xml` first.
2. Keep locale files key-for-key aligned with source strings (except `translatable="false"`).
3. Do not change key names during translation work.
4. Keep formatter placeholders exactly compatible (`%1$s`, `%d`, `%1$.1f`, etc.).
5. Preserve escape/protocol semantics (`\n`, XML entities, simple inline tags).
6. Every localization PR must pass the string resource guard:
   - `python3 scripts/check-string-resources.py`

## CI Guard

`build-test-lint` in `.github/workflows/android-build.yml` runs:

```bash
python3 scripts/check-string-resources.py
```

Behavior:

- If no `values-*/strings.xml` files exist, the check exits cleanly.
- If locale files exist, the check fails on:
  - missing keys
  - extra keys
  - placeholder mismatch

## Practical Translation Flow

1. Prepare scope:
   - Export changed keys from `values/strings.xml` (or provide full file when bootstrapping a new locale).
2. Translate with guarded prompt (template below).
3. Apply translation to locale file (`values-fi/strings.xml`).
4. Run guard locally:
   - `python3 scripts/check-string-resources.py`
5. Run compile smoke:
   - `./gradlew :app:compileDebugKotlin --no-daemon`
6. Manual UI spot check on device for critical flows.
7. Update `CHANGELOG.md` if change is notable.

## Suggested Batch Strategy

- Small delta batches for normal PRs.
- Full-file translation only when introducing a new locale.
- Keep a lightweight glossary for recurring domain terms (trainer, workout, cadence, summary, mock mode).

## Localization Checklist

Use this checklist for each translation batch.

- [ ] Source strings updated in `app/src/main/res/values/strings.xml`
- [ ] Locale file updated (`app/src/main/res/values-fi/strings.xml`)
- [ ] No key renames
- [ ] Placeholder sets match source for every translated key
- [ ] Escapes/newlines/tags preserved where relevant
- [ ] `python3 scripts/check-string-resources.py` passes
- [ ] `./gradlew :app:compileDebugKotlin --no-daemon` passes
- [ ] Critical screens reviewed in Finnish on device/emulator
- [ ] `CHANGELOG.md` updated (if notable)

## Translation Prompt Template

```text
You are translating Android string resources from English to Finnish.

Hard constraints (must follow):
1) Keep every `name` key exactly unchanged.
2) Keep formatter placeholders exactly unchanged (examples: `%1$s`, `%2$d`, `%1$.1f`).
3) Keep escape sequences and control chars unchanged (`\\n`, `\'`, XML entities).
4) Preserve inline XML tags if present (`<b>`, `<i>`, `<u>`, etc.).
5) Do not add, remove, or reorder keys.
6) Return valid Android `<resources>` XML only.
7) Tone: concise, clear mobile UI language in Finnish.

Quality checks before answering:
- Placeholder count and types must match source per key.
- Output must be parseable XML for Android `strings.xml`.

Input XML:
<PASTE_SOURCE_OR_SCOPE_XML_HERE>
```
