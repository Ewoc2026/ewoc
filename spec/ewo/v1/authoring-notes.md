# Canonical `.ewo` v1 Authoring Notes

## Status

- status: non-normative guidance
- audience: workout authors, editor implementers, import/export tooling
- scope: authoring recommendations that sit beside `spec.md` and must not widen the canonical `.ewo` v1 contract

## Why This File Exists

The normative spec intentionally leaves presentation policy partly open so the
project can freeze the file contract without freezing every editor or runtime
decision at the same time.

This note reduces day-to-day authoring ambiguity without changing what counts as
valid canonical `.ewo` v1 JSON.

## Message Placement

Use workout-root messages for guidance that applies to the whole session.

Examples:

- one short intro before the rider starts
- one high-level safety or pacing reminder that applies across multiple segments

Use segment messages for cues that matter only at a specific segment boundary.

Examples:

- a start-of-interval execution cue
- a recovery transition reminder
- a warning tied to one specific effort block

Prefer the nearest valid scope that explains the cue. This avoids future editor
surfaces guessing whether the same sentence should appear once or repeat on
every child segment.

Starting in v1.5, prefer segment `label` for short rider-facing block names and
segment `note` for authoring commentary that should not be shown as an in-ride
cue. Do not overload `messages` with metadata that belongs in `label` or
`note`.

## Message Content

Messages are rider-facing presentation metadata, not control data.

Keep each message aligned with the following intent:

- explain what the rider should notice or do
- keep wording short enough for small-screen presentation
- preserve the same coaching intent across `text.default` and translations

Avoid using messages to carry information that belongs somewhere else:

- do not encode hidden execution flags in message text
- do not rely on message wording to change targets, durations, or repeat counts
- do not use translations to smuggle editor-only metadata

## Localization Guidance

### `text.default`

Always write `text.default` as complete rider-facing copy that still makes sense
when no translation is selected.

Authoring recommendation:

- treat `text.default` as the fallback copy every consumer can show safely
- choose one clear source phrasing and keep translations semantically equivalent
- omit a message entirely instead of keeping a placeholder such as `TODO`

### `text.translations`

`text.translations` is optional. Add it only when the translated copy is ready
for the same review quality as `text.default`.

Authoring recommendations:

- omit missing locales instead of adding blank values
- keep each translation aligned with the same coaching intent as `text.default`
- prefer stable BCP 47 language tags such as `fi`, `en`, or `en-GB`

Canonical `.ewo` v1 only requires locale keys to be non-empty. The project
intentionally does not freeze stricter locale-key syntax in v1.0, so tools must
not reject a key only because it does not match a specific external locale
standard.

Treat the BCP 47 tag examples above as interoperability guidance, not as an
additional validation rule. This keeps authored files compatible with the
current permissive parser while still documenting the preferred locale-key form
for producers and downstream tooling.

## Recommended Patterns

### Good: complete fallback plus one reviewed translation

```json
{
  "kind": "instruction",
  "when": "start",
  "text": {
    "default": "Stay inside the band without chasing spikes.",
    "translations": {
      "fi": "Pysy alueella ilman että jahtaat piikkejä."
    }
  }
}
```

Why this is a good pattern:

- `text.default` stands on its own
- the locale key is non-empty
- the translation keeps the same rider-facing intent as the fallback text

### Good: end-relative cue in v1.5

```json
{
  "kind": "transition",
  "when": {
    "anchor": "end",
    "offset_sec": -5
  },
  "text": {
    "default": "Prepare to stop."
  }
}
```

Why this is a good pattern:

- the cue is authored relative to the meaningful boundary instead of duplicated
  into ad hoc countdown text
- the timing rule survives editor round-trips without guessing
- legacy consumers can still preserve the message object even if their
  presentation policy is simple

### Good: omit translations when fallback copy is enough

```json
{
  "kind": "transition",
  "when": "start",
  "text": {
    "default": "Back off and prepare for the next effort."
  }
}
```

Why this is a good pattern:

- there is no blank placeholder locale
- every consumer can still render useful guidance
- later translations can be added without changing message semantics

### Avoid: empty locale keys or placeholder copy

```json
{
  "kind": "intro",
  "when": "start",
  "text": {
    "default": "Ready",
    "translations": {
      "": "Valmis",
      "fi": "TODO"
    }
  }
}
```

Why this should be avoided:

- the empty locale key is invalid in canonical `.ewo` v1
- placeholder text creates ambiguity for editor previews and runtime fallback
- translation quality is harder to review when fallback and localized copy are
  not kept at the same standard

## Editor And Tooling Notes

Editors and import/export tools should preserve authored message objects exactly
unless a user explicitly edits them.

In particular:

- do not normalize locale keys into a stricter schema than canonical v1 defines
- do not infer extra timing or execution semantics from message `kind`
- do not rewrite valid localized message content just to match a preferred house style

These recommendations reduce accidental drift while the canonical `.ewo` v1
contract stays strict about structure but intentionally light on presentation
policy.
