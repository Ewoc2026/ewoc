# Canonical `.ewo` v1 Specification

## Status

- status: freeze-candidate scaffold
- canonical root identity: `format = "ewo"`, `version = "1.x"`
- intended external audience: Android import pipeline, future Linux/Windows editor, validator tooling
- current transition rule: if this scaffold conflicts with Android parser behavior, resolve the drift before treating the bundle as frozen

## Freeze Readiness

The following surfaces are ready to freeze for canonical `.ewo` v1.x unless a
later repo decision explicitly reopens them.

- root identity: `format = "ewo"`, `version = "1.x"`
- closed-world field sets at every object level
- required stable segment IDs with whole-workout uniqueness
- canonical segment kinds, target shapes, and repeat v1 restrictions
- heart-rate control object shape and semantic bounds
- message envelope shape and the rule that messages never change execution semantics
- the distinction between `structural`, `semantic`, and `runtime_executable`

The following governance decisions are now explicit for canonical `.ewo` v1.x.

- localized workout-root `title_localized` / `description_localized`
  companions are part of canonical `.ewo` `1.6`; they are no longer
  provisional future work inside the v1 line
- locale-key validation stays permissive for any non-empty key across canonical
  localized-text objects, while BCP 47 tags remain the preferred producer
  guidance for interoperability
- locale-selection fallback policy is intentionally outside the core file-format
  contract, so runtimes and editors may document their own selection rules
  without silently changing authored JSON validity
- formatting and pretty-printing remain tooling policy rather than canonical
  file-contract requirements
- compiled-step or repeat-origin export artifacts remain outside authored
  canonical `.ewo` and require an explicit later profile if they ever become
  public
- runtime-profile labels in conformance notes are descriptive compatibility
  labels, not authored-file compatibility guarantees

## Scope

This document defines the file-format contract for canonical `.ewo` workouts.
It does not define Android-specific import sniffing, UI presentation rules,
runtime error wording, or trainer control-loop tuning.

## Normative Package

The `.ewo` v1 contract is expected to freeze as a package, not as a single file.

- `spec.md` defines the human-readable contract and governance notes.
- `schema.json` defines structural JSON constraints.
- `conformance/manifest.json` defines machine-checkable parser expectations.
- `examples/` provides valid authoring fixtures.

## Validation Levels

The bundle uses three validation levels so editor tooling can distinguish
authoring validity from execution readiness.

1. `structural`
   - JSON is well-formed.
   - object shapes, enums, and required fields match `schema.json`.
   - unknown fields are rejected.

2. `semantic`
   - cross-field invariants hold.
   - segment IDs are unique across the whole workout tree.
   - durations, repeat counts, control bounds, and target ranges are valid.
   - any workout that uses `heart_rate` targets provides a complete root `control` object.

3. `runtime_executable`
   - a specific runtime profile can execute the workout end to end.
   - this level is runtime-profile-specific and must not be conflated with file validity.

## Root Object

The root object is a strict JSON object.

### Required fields

- `format`
- `version`
- `title`
- `segments`

### Optional fields

- `uid` (v1.5+)
- `revision` (v1.5+)
- `description`
- `title_localized` (v1.6+)
- `description_localized` (v1.6+)
- `difficulty` (v1.3+)
- `tags` (v1.3+)
- `control`
- `messages`

### Root invariants

- `format` must equal `ewo`.
- `version` must equal one of the explicitly supported `1.x` minor versions.
- `title` must be non-blank after trimming.
- if `title_localized` is present, it must use the canonical localized-text shape and `title_localized.default` must match root `title` after trimming normalization
- if `description_localized` is present, root `description` must also be present, and `description_localized.default` must match root `description` after trimming normalization
- `segments` must contain at least one segment after semantic validation.
- unknown fields must be rejected.

## Segment Identity

Every segment requires a stable technical `id`.

- applies to top-level segments
- applies to repeat container segments
- applies to repeat child segments

### ID rules

- type: JSON string
- pattern: `^[a-z][a-z0-9_-]{0,63}$`
- length: 1 to 64 characters
- uniqueness scope: whole workout tree

IDs are technical authoring identities. They are not rider-facing labels and do
not replace `title` or `messages`.

Starting in v1.5, segments may also include optional rider-facing `label` and
authoring-only `note` metadata. These fields do not replace the technical `id`.

## Segment Kinds

Canonical `.ewo` v1 supports the following segment kinds.

### `steady`

Required fields:

- `id`
- `type = "steady"`
- `duration_sec`
- `target`

Optional fields:

- `label` (v1.5+)
- `note` (v1.5+)
- `messages`

### `ramp`

Required fields:

- `id`
- `type = "ramp"`
- `duration_sec`
- `from_target`
- `to_target`

Optional fields:

- `label` (v1.5+)
- `note` (v1.5+)
- `messages`

v1 constraint:

- both ramp targets must use the same metric and must both be `power` or both
  be `ftp_percent`; `ftp_percent` ramps require version `1.1` or later

### `free_ride`

Required fields:

- `id`
- `type = "free_ride"`
- `duration_sec`

Optional fields:

- `label` (v1.5+)
- `note` (v1.5+)
- `cadence`
- `messages`

v1 constraint:

- `free_ride` requires version `1.5` or later

### `repeat`

Required fields:

- `id`
- `type = "repeat"`
- `count`
- `segments`

Optional fields:

- `label` (v1.5+)
- `note` (v1.5+)
- `messages`

v1 constraints:

- `count` must be greater than zero
- `segments` must contain at least two child segments
- repeat children may be `steady` or `free_ride` segments
- nested `repeat` is out of scope for v1
- `ramp` inside `repeat` is out of scope for v1

## Targets

### Power target

Power targets use the following shape.

```json
{
  "metric": "power",
  "value": 180
}
```

Semantic rule:

- `value` must be a positive integer

### FTP-percent target

FTP-percent targets express intensity as a fraction of the rider's FTP and use
the following shape.

```json
{
  "metric": "ftp_percent",
  "value": 0.90
}
```

Semantic rules:

- `value` must be a number in the range `0.10..2.50` (for example `0.90` for
  90% FTP)
- the rider's FTP must be supplied at compile time; if absent, parsing returns
  `FTP_REQUIRED_FOR_FTP_PERCENT`

Version gate:

- `ftp_percent` requires version `1.1` or later

`ftp_percent` is valid as both a `steady` target and as a ramp endpoint target.
Both `from_target` and `to_target` of the same ramp must use the same metric.

### Heart-rate target

Heart-rate targets use the following shape.

```json
{
  "metric": "heart_rate",
  "range": {
    "low": 145,
    "high": 155
  }
}
```

Semantic rules:

- `low` and `high` must satisfy `40 <= low < high <= 220`
- any workout that contains a heart-rate target must define root `control`

### Heart-rate-relative target

Heart-rate-relative targets express intensity as a fraction of a reference
heart rate and use the following shape.

```json
{
  "metric": "heart_rate_relative",
  "reference": "hr_max",
  "range": {
    "low": 0.80,
    "high": 0.90
  }
}
```

`reference` must be one of:

- `hr_max` for percentage of maximum heart rate
- `heart_rate_reserve` for percentage of heart-rate reserve (Karvonen)
- `lthr` for percentage of lactate-threshold heart rate

Semantic rules:

- `low` and `high` must satisfy `0 < low < high`
- any workout that contains a heart-rate-relative target must define root
  `control`

Version gate:

- `heart_rate_relative` requires version `1.4` or later

## Control

`control` is a root-level object for workouts that need heart-rate control or
heart-rate safety boundaries.

Required fields when `control` is present:

- `initial_power_watts`
- `min_power_watts`
- `max_power_watts`
- `signal_loss_power_watts`
- `hr_upper_cap_bpm`

Semantic rules:

- if `control` is present, it must be complete
- `min_power_watts` and `max_power_watts` must be positive integers
- `min_power_watts < max_power_watts`
- `min_power_watts <= initial_power_watts <= max_power_watts`
- `min_power_watts <= signal_loss_power_watts <= max_power_watts`
- `hr_upper_cap_bpm` must satisfy `40 <= hr_upper_cap_bpm <= 220`
- runtimes may add stricter device-specific clamps, but must not weaken authored hard constraints

## Messages

Messages are optional rider-facing presentation metadata.

Messages may appear on:

- the workout root
- `steady` segments
- `ramp` segments
- `free_ride` segments
- `repeat` segments

### Message shape

- `kind` ∈ `{intro, instruction, transition, warning, motivation}`
- legacy `when = "start"` remains valid across all supported `1.x` minors
- starting in v1.5, `when` may also be an object with:
  - `anchor` ∈ `{start, end}`
  - `offset_sec` as a signed integer offset from that anchor
- `text.default` is required and must be non-blank after trimming
- `text.translations` is optional and maps non-empty locale keys to translated strings
- every translation value must be non-blank after trimming
- locale-key syntax is intentionally left open in v1.0 beyond the non-empty-key rule; consumers may treat keys as opaque strings
- for interoperability, producers should prefer stable BCP 47 language tags such as `fi`, `en`, or `en-GB` when choosing locale keys, while consumers must continue accepting any non-empty key

### Message invariants

- messages must never change targets, durations, repeat counts, or safety behavior
- presentation behavior is runtime-specific and is not part of the core file-format contract

### Locale keys and selection policy

- the non-empty locale-key rule applies to every canonical localized-text object,
  including `text.translations`, `title_localized.translations`, and
  `description_localized.translations`
- consumers must not reject an otherwise valid file only because a locale key
  does not match a stricter locale syntax profile
- producers should prefer stable BCP 47 tags when choosing locale keys for new
  files and interoperability-facing tooling
- canonical `.ewo` v1 does not freeze one presentation fallback order for
  choosing among authored translations; that selection policy belongs to the
  consuming runtime or editor, not to authored JSON validity

Structured timing example:

```json
{
  "kind": "instruction",
  "when": {
    "anchor": "end",
    "offset_sec": -5
  },
  "text": {
    "default": "Prepare to stop."
  }
}
```

## Repeat Expansion Metadata

Repeat expansion creates a deterministic flat timeline in the current Android implementation.

The following origin metadata is useful for import handoff, editor mapping, and
debugging, but it is runtime-derived metadata rather than authored file content.

- `sourceSegmentId`
- `enclosingRepeatSegmentId`
- `repeatIterationIndex`

Canonical `.ewo` v1.0 does not make this compiled metadata part of the external
file-format contract.

- authored `.ewo` files must not include compiled repeat-origin fields
- runtimes and importers may derive and persist equivalent metadata internally
- third-party editors must not depend on round-tripping compiled metadata through authored `.ewo` payloads
- any future exported compiled artifact must use an explicit later spec/profile instead of silently widening v1.0 authored JSON

## Compatibility And Extensions

Canonical `.ewo` v1.0 is strict and closed-world.

### Conformance runtime profiles

- runtime-profile labels used in conformance notes are descriptive names for
  tested execution environments or validation contexts
- changing a runtime-profile label or adding a new label does not by itself
  change the canonical authored-file contract
- third-party tools must not infer authored `.ewo` compatibility solely from a
  matching runtime-profile label

### Unknown fields

- unknown fields are invalid at every object level
- validators and parsers must reject unknown fields instead of silently ignoring or preserving them
- a producer must not claim canonical `.ewo` v1.0 output if it injects extra fields that are not defined by this package
- field names that merely look vendor-scoped, such as `vendor_*` or `x_*`, are still unknown unless a later spec revision explicitly defines them

### Minor versions

- parsers may accept only the exact minor versions they explicitly implement
- a parser is not required to accept a later `1.x` minor it has not implemented
- any future `1.x` revision must preserve the meaning of already-defined v1.0 fields
- any future `1.x` revision must not remove or repurpose existing required fields
- additive fields, if ever introduced in a later `1.x`, require an explicit version bump and updated parser support

The currently standardized additive root-metadata extension is:

- `title_localized` / `description_localized` in `1.6`

This policy intentionally favors deterministic interoperability over forward
compatibility-by-guessing.

### Formatting and round-tripping

- canonical `.ewo` v1 does not define a byte-for-byte formatting or
  pretty-printing profile
- whitespace, object key order, and line wrapping are not part of the authored
  compatibility contract
- tools may reformat valid canonical `.ewo` payloads as long as they preserve
  the same semantic content and do not inject unsupported fields or versions
- if a later public toolchain wants a house formatting profile, it must publish
  that as tooling guidance or a separate profile rather than implying a new core
  format rule

### Third-party extensions

- canonical `.ewo` v1.0 has no in-band vendor-field or namespace escape hatch
- third-party tools that need proprietary metadata must use a sidecar file, wrapper container, or non-canonical profile instead of injecting fields into canonical objects
- if the project later standardizes extension points, it will do so through an explicit spec revision rather than ad hoc vendor keys

### Legacy migration stance

- migration from legacy `ergo_workout` payloads is importer behavior, not part of the canonical `.ewo` file contract
- canonical `.ewo` v1.0 does not embed legacy-compatibility hints or fallback parsing rules

## Explicit Non-Goals For v1

The following are intentionally out of scope for canonical `.ewo` v1.

- nested repeats
- ramps inside repeat blocks
- heart-rate ramps
- localized workout-root `title` / `description` metadata in versions `1.0` through `1.5`
- message template variables
- cadence and RPE target metrics
- Android-specific UI rendering rules
- trainer control-loop implementation details
