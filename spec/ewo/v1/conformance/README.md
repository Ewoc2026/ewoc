# `.ewo` v1 Conformance Scaffold

This directory holds parser-facing conformance fixtures for the canonical `.ewo`
bundle.

## Intent

- keep valid examples and invalid edge cases in one machine-readable manifest
- let Android parser tests consume the same fixtures that future editor tooling will use
- separate file validity from runtime executability through explicit expectations
- keep the fixture surface scoped to authored `.ewo` JSON rather than runtime-derived repeat metadata

## Files

- `manifest.json` — list of conformance cases and expected outcomes
- `fixtures/` — negative or edge-case inputs that should stay out of the public examples set

## Runner Expectations

The first runner is implemented in Android unit tests. Future external tooling can
reuse the same manifest shape or replace it with a stricter shared runner once the
spec package is frozen.

- canonical v1.0 conformance expects exact `version = "1.0"` support rather than optimistic `1.x` acceptance
- canonical v1.0 conformance treats unknown fields, including ad hoc vendor keys, as parser failures
- fixtures describe authored-file JSON only; runtime-derived repeat-origin metadata stays outside this manifest unless a later spec/profile standardizes it
