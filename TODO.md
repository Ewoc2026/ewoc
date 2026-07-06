# TODO

This file tracks current repository work. Historical implementation proposals
belong in the relevant spec or design document, not in the active TODO list.

## Active Follow-Ups

### `.ewo` root metadata localization

Status:
- Canonical `.ewo` `1.6` root `title_localized` and
  `description_localized` are implemented in the spec, schema, parser,
  conformance fixtures, and Android import preservation.
- Android display-time localized root title/description resolution is still
  deferred.
- Android editor authoring for localized root metadata is still deferred.

Canonical sources:
- `spec/ewo/v1/spec.md`
- `spec/ewo/v1/schema.json`

Remaining work:
- Resolve localized root title/description at Android display time.
- Add Android editor model/UI authoring support if localized root metadata
  becomes an exposed workflow.
- Archive or rewrite older proposal references during a broader EWO docs
  cleanup pass.

## Deferred Product Decisions

### Localized root metadata authoring

The parser/import path preserves canonical localized root metadata, but the
Android editor still exposes the existing plain-string title/description model.
Do not expand authoring until there is a concrete workflow need for editing
localized workout-level metadata on Android.

## Completed Or Archived Historical Proposals

### `.ewo` v1.2 cadence guidance

Status:
- Implemented in the canonical EWO spec, schema, parser/validator, compiler,
  Android import boundary, and runtime mapping.

Canonical source:
- `spec/ewo/v1/spec.md`

Current invariant:
- Cadence is an optional rider coaching signal on steady and ramp segments.
- Cadence never affects trainer resistance or execution semantics.
- Repeat segments do not accept their own cadence field.
- Valid cadence ranges remain bounded by the canonical semantic validator.

Historical rationale:
- The change was additive and kept existing `1.0` / `1.1` files valid.
- It avoided changing workout execution semantics while allowing authoring and
  display surfaces to carry cadence guidance.
