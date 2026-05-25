# `.ewo` Specification Workspace

This directory holds the canonical `.ewo` format governance bundle.

## Purpose

- give the project one place to freeze the external `.ewo` contract before editor work starts
- keep Android import/runtime behavior separate from file-format rules
- make parser drift visible by sharing fixtures between spec assets and app tests

## Transition Rule

The bundle under `spec/ewo/v1/` is currently a freeze candidate scaffold with
implemented parser coverage through v1.6.
Until the contract is formally frozen, the Android parser and validator remain the
compatibility oracle for unresolved edge cases. Any drift between this bundle and
the parser should be treated as a bug to resolve before Linux/Windows editor work
continues.

Current repository note:

- the canonical spec/schema/conformance baseline now includes `.ewo` `1.6`
  root localized metadata companions
- some authoring workflows in this repository still intentionally target
  canonical `.ewo` `1.5` for now, such as the bundled workout authoring prompt
- format minor versions and shared module/editor release lines are intentionally
  decoupled; see `spec/ewo/versioning.md`

## Bundle Layout

- `spec/ewo/v1/spec.md` — normative contract draft for `.ewo` v1
- `spec/ewo/versioning.md` — versioning note for format minors versus
  library/editor release lines
- `spec/ewo/v1/authoring-notes.md` — non-normative authoring guidance for messages and localization
- `spec/ewo/v1/chatgpt-multilingual-workout-prompt.md` — reusable operator prompt for one multilingual workout draft at a time
- `spec/ewo/v1/schema.json` — machine-readable structural schema
- `spec/ewo/v1/examples/` — valid example workouts that reflect the current parser contract, including v1.6 root localized metadata companions
- `spec/ewo/v1/conformance/` — manifest and fixtures for parser conformance checks
