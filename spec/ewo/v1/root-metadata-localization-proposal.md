# Historical Rationale For Canonical `.ewo` v1.6 Localized Workout Root Metadata

## Status

- status: historical rationale document
- active source of truth: `spec/ewo/v1/spec.md`, `spec/ewo/v1/schema.json`,
  `spec/ewo/v1/examples/root-localized-metadata-v1-6.ewo.json`, and
  `spec/ewo/v1/conformance/manifest.json`
- scope: records why canonical `.ewo` `1.6` chose additive root localized
  metadata companions instead of widening the existing root field types
- current app status: Android import preserves these fields, while Android UI
  display-time resolution and Android editor authoring remain deferred

## Resolved Outcome

Canonical `.ewo` `1.6` standardized optional root `title_localized` and
`description_localized` companion fields while keeping root `title` and
`description` as the canonical fallback strings.

## Adopted Recommendation

The repository adopted the additive companion-field approach instead of changing
`title` or `description` into objects.

Adopted fields:

- `title_localized`
- `description_localized`

## Why This Shape Is Preferred

Changing `title` from `string` to `object` would widen the meaning of an
already-defined required field and force every parser, importer, editor, and UI
surface to handle a breaking type change.

Adding companion fields instead:

- preserves the existing root contract for older files
- keeps migration additive rather than structural
- lets consumers fall back cleanly to the existing string fields
- reuses the same localized-text shape already used by message text

## Adopted JSON Shape

```json
{
  "format": "ewo",
  "version": "1.6",
  "title": "Threshold Builder",
  "description": "3 x 8 min near threshold with controlled recoveries.",
  "title_localized": {
    "default": "Threshold Builder",
    "translations": {
      "fi": "Kynnystehon rakentaja",
      "de": "Schwellenaufbau"
    }
  },
  "description_localized": {
    "default": "3 x 8 min near threshold with controlled recoveries.",
    "translations": {
      "fi": "3 x 8 min lahella kynnysta hallituilla palautuksilla.",
      "de": "3 x 8 Min nahe der Schwelle mit kontrollierten Erholungen."
    }
  },
  "segments": [
    {
      "id": "main_set",
      "type": "steady",
      "duration_sec": 480,
      "target": {
        "metric": "power",
        "value": 220
      }
    }
  ]
}
```

## Adopted Rules

### Root field behavior

- `title` remains required and must stay a non-blank string
- `description` remains optional and, when present, stays a string
- `title_localized` is optional
- `description_localized` is optional

### Localized field shape

Both localized companion fields should reuse the existing localized-text shape:

```json
{
  "default": "Threshold Builder",
  "translations": {
    "fi": "Kynnystehon rakentaja"
  }
}
```

Localized-text rules kept for this feature:

- `default` is required and must be non-blank after trimming
- `translations` is optional
- translation locale keys must be non-empty
- translation values must be non-blank after trimming
- for interoperability, producers should prefer stable BCP 47 language tags

### Consistency rules

To avoid ambiguous fallback behavior, the implemented `1.6` slice defined the
following semantic invariants:

- if `title_localized` is present, `title_localized.default` must equal `title`
  after trimming normalization
- if `description_localized` is present and `description` is present,
  `description_localized.default` must equal `description` after trimming
  normalization
- if `description_localized` is present while `description` is absent, semantic
  validation fails

This kept root fallback behavior symmetrical with `title`.

## Runtime Resolution Recommendation

Consumers that support `1.6` should resolve localized root metadata
using the same precedence as localized message text:

1. exact locale-tag match
2. language-only match
3. `default`
4. root fallback string field (`title` or `description`) if needed

This keeps rider-facing metadata behavior aligned across workout names,
descriptions, and in-ride messages.

## Compatibility Notes

The adopted `1.6` design is intentionally additive.

- canonical `.ewo` `1.0` through `1.5` would remain unchanged
- parsers that only support `1.0` through `1.5` would continue rejecting `1.6`
  until explicitly updated
- older consumers must not guess at unknown fields inside older minor versions

## Remaining Deferred Follow-Ups

- Should Android and other consumers resolve localized root metadata at display
  time now, or continue using fallback root strings until a dedicated UI slice
  is scheduled?
- Should localized root metadata become part of the Android editor UI, or stay
  preserved-but-not-authored there while desktop/editor extraction work matures?
- Should future library or summary views prefer localized root title over other
  rider-facing fallback text when both are available?
