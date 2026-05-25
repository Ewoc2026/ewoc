# TODO

## Format Follow-Ups

### `.ewo` v1.6 — localized workout root metadata

**Status:** implemented in the canonical spec, schema, parser, conformance fixtures, and Android import preservation
**Canonical source:** see `spec/ewo/v1/spec.md` and `spec/ewo/v1/schema.json`
**Historical rationale:** see `spec/ewo/v1/root-metadata-localization-proposal.md`

#### Current state

- `title_localized` and `description_localized` are now part of canonical
  `.ewo` `1.6`
- root `title` stays the required fallback string
- root `description` stays the optional fallback string
- Android import preserves localized root metadata
- Android UI display-time localized root resolution is still deferred
- Android editor authoring for localized root metadata is still deferred

#### Canonical JSON shape

```json
{
  "format": "ewo",
  "version": "1.6",
  "title": "Threshold Builder",
  "description": "3 x 8 min near threshold with controlled recoveries.",
  "title_localized": {
    "default": "Threshold Builder",
    "translations": {
      "fi": "Kynnystehon rakentaja"
    }
  },
  "description_localized": {
    "default": "3 x 8 min near threshold with controlled recoveries.",
    "translations": {
      "fi": "3 x 8 min lahella kynnysta hallituilla palautuksilla."
    }
  }
}
```

#### Implemented rules

- Keep `title` required as a string fallback
- Keep `description` optional as a string fallback
- Add optional `title_localized` and `description_localized`
- Reuse the existing localized-text shape: `default` plus optional
  `translations`
- Prefer stable BCP 47 locale keys for interoperability
- If localized root metadata is present, define semantic consistency rules so
  the localized `default` value matches the root fallback string

#### Why this direction remains correct

- Avoids changing the type of the already-defined required `title` field
- Keeps the change additive for a later minor version
- Reuses the message-localization model already present in canonical `.ewo`
- Gives UI and import pipelines a clean fallback path

#### Remaining follow-ups

- Android display-time localized root title/description resolution
- Android editor model/UI authoring support if localized root metadata becomes
  an exposed workflow
- cleanup of historical proposal references once the broader EWO cleanup slice
  archives or rewrites them

### `.ewo` v1.2 — cadence guidance

**Status:** implemented in commit on main
**Design:** see conversation 2026-03-09 (cadence design session)

#### Summary

Add an optional segment-level `cadence` hint to `.ewo` steady and ramp segments.
Cadence is a rider coaching signal — it never affects trainer resistance or execution semantics.

#### JSON shape

```json
{
  "id": "interval",
  "type": "steady",
  "duration_sec": 300,
  "target": { "metric": "ftp_percent", "value": 0.90 },
  "cadence": { "low": 95, "high": 105 }
}
```

#### Rules

- Optional field on `steady` and `ramp` segments only (not on `repeat`)
- Schema: `{ "low": int, "high": int }`, both required when present
- Semantic: `30 <= low <= high <= 200`
- One new error code: `INVALID_CADENCE_RANGE("invalid_cadence_range")`
- `repeat` segment allowed keys do NOT include `cadence`
- Cadence is carried through normalized → compiled → imported step as nullable metadata
- `ImportedErgoWorkoutExecutionMapper` ignores it entirely

#### New types

- `ParsedEwoCadenceRange(low: Int, high: Int)` — internal, schema layer
- `EwoCadenceRange(low: Int, high: Int)` — internal, normalized/compiled layer
- `ImportedErgoWorkoutCadenceRange(low: Int, high: Int)` — public, import boundary

#### Files to touch

- `EwoWorkoutModel.kt` — new types, new error code
- `EwoWorkoutSchemaValidator.kt` — parse cadence on steady/ramp; forbid on repeat
- `EwoWorkoutSemanticValidator.kt` — range check; propagate through normalized segments
- `EwoWorkoutRepeatExpansionCompiler.kt` — carry cadence into compiled steps
- `WorkoutImportService.kt` — carry cadence into `ImportedErgoWorkoutStep`
- `EwoWorkoutSemanticValidator` version check: accept `"1.2"` in `supportedVersions`

#### Versioning

`version: "1.2"` — purely additive. v1.0 and v1.1 files remain valid unchanged.
