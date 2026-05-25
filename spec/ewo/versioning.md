# `.ewo` Format And Library Versioning

## Status

- status: active reference note
- date: 2026-03-19
- scope: explains how canonical `.ewo` spec versions relate to shared module,
  editor, and future public artifact release lines

## Why This Note Exists

The future public EWO boundary should not imply that one `.ewo` minor version
must always map to one matching library or editor release number.

Canonical format evolution and implementation release cadence are related, but
they are not the same version line.

## Versioning Rules

### `.ewo` format versions

- `.ewo` `1.x` minor versions version the authored file-format contract
- format changes become canonical only when the normative package is updated
  together:
  - `spec/ewo/v1/spec.md`
  - `spec/ewo/v1/schema.json`
  - `spec/ewo/v1/conformance/manifest.json`
  - relevant canonical examples or fixtures
- a new supported `.ewo` minor requires explicit parser and validator support;
  support must not be inferred from a library release number alone

### Shared modules and editor releases

- `:modules:ewo-core`, `:modules:ewo-editor-model`,
  `:modules:ewo-editor-commands`, and any future public desktop/editor package
  may release on their own cadence
- a module or editor release may ship bug fixes, performance work, API cleanup,
  documentation updates, or tooling improvements without changing the canonical
  `.ewo` format version
- supporting an already-defined `.ewo` minor in a newer library release does not
  require the library release number to match that format minor

### Compatibility communication

- future public release notes should publish a compatibility matrix such as
  "artifact X supports canonical `.ewo` minors A through B"
- a library release must not imply support for a newer `.ewo` minor unless that
  support is explicitly implemented and documented
- a canonical `.ewo` spec note must not imply that all implementations upgrade
  in lockstep

## Practical Interpretation For Extraction

When the EWO boundary becomes public:

- the spec line should communicate authored-file compatibility
- shared module and editor release lines should communicate implementation
  delivery cadence
- public documentation should connect the two with an explicit support matrix
  instead of numeric alignment assumptions
