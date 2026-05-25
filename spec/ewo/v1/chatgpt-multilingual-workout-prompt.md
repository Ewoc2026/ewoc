# ChatGPT Prompt Template For Multilingual Canonical `.ewo` Workouts

## Status

- status: non-normative operator template
- audience: human operators using ChatGPT to draft canonical `.ewo` workouts
- scope: reusable prompt for one workout at a time across the planned eight-workout batch
- current workflow note: this template is intentionally pinned to canonical
  `.ewo` `1.5` for the current bundled-workout authoring flow even though the
  broader canonical spec/parser baseline now includes `.ewo` `1.6`

## Why This Template Uses One Workout Per Run

Generating one workout per response is easier to review, easier to validate, and
less likely to drift into invalid JSON than asking for all eight workouts in one
reply.

Reuse the template once per workout brief instead of batching the whole catalog
into one response.

## Recommended Operator Inputs

Fill in these fields before sending the prompt:

- workout brief
- target duration
- target metric and intensity constraints
- requested locale tags for `text.translations`
- whether heart-rate targets or HR safety control are allowed
- any required warm-up, cooldown, cadence, or repeat-pattern constraints

## Prompt Template

```text
You are authoring exactly one canonical `.ewo` workout for this repository.

Goal:
- Return exactly one valid canonical `.ewo` `1.5` JSON object.
- Base it only on the workout brief I provide after this instruction block.
- Keep rider-facing segment cues short and action-oriented.
- Put longer rationale only at workout level or repeat-block level when it adds value.
- Reuse this same prompt for each planned workout instead of batching multiple workouts into one reply.
- Treat the `1.5` target as an intentional repository authoring constraint for
  this workflow, not as a statement that canonical `.ewo` stops at `1.5`.

Hard output rules:
1. Return JSON only. No markdown fences. No prose before or after the JSON.
2. Set `"format": "ewo"` and `"version": "1.5"`.
3. Use only fields supported by canonical `.ewo` `1.5`.
4. Do not invent extension fields or vendor-prefixed fields.
5. Do not output `title_localized`, `description_localized`, or any other localized root metadata.
6. Keep root `title` as a plain string.
7. Keep root `description` as a plain string when used.
8. Keep every segment `id` unique, stable, lowercase, and technical rather than rider-facing.
9. If a segment includes `label` or `note`, keep them as plain strings. Do not try to localize them.
10. Pretty-print the final JSON with two-space indentation. Do not return minified one-line JSON.

Authoring rules:
1. Use `messages` only for rider-facing guidance, never for execution semantics.
2. Prefer segment `label` for short block names and `note` for authoring commentary that should not appear as an in-ride cue.
3. Prefer one workout-level intro or one repeat-block cue over repeating the same rationale on every child segment.
4. Add a segment-level message only when the rider needs an immediate boundary cue.
5. Segment-level cues must stay short:
   - preferred: 2 to 6 words
   - hard cap: 9 words per locale
6. Workout-level or repeat-block rationale may be a little longer, but keep it to one short sentence per locale.
7. Omit a message instead of repeating the same coaching idea in multiple places.
8. Keep `text.default` complete and rider-facing.
9. Add `text.translations` only for the locale tags I request.
10. Use stable BCP 47 locale tags such as `fi`, `en`, or `en-GB` for translation keys.
11. Keep every translation semantically aligned with `text.default`.
12. If a translation would become awkward or overly long, shorten it while preserving the same coaching intent.
13. Never mention `.ewo`, JSON, "segments", "repeat blocks", "building blocks", or other authoring/meta terminology inside rider-facing `messages`.
14. If a segment-level idea needs more than the short-cue limit, move that rationale to workout or repeat-block scope or omit it entirely.

Structure rules:
1. Use `segments` everywhere. Never use `steps`.
2. Use only canonical segment types supported in `.ewo` `1.5`.
3. Use `repeat` only with supported child segments.
4. Use `when: "start"` by default for messages.
5. Use structured end-relative timing only when the brief clearly needs it.
6. If any segment uses a heart-rate target, include a complete root `control` block.
7. Keep heart-rate workouts conservative. Do not create aggressive HR intervals or hidden adaptive logic.

Style rules:
1. Root `title`, root `description`, `label`, and `note` should stay in English unless I explicitly ask for another source language.
2. Rider cues should sound direct and calm, not verbose, technical, or motivational for its own sake.
3. Do not narrate obvious transitions if the segment structure already makes them clear.
4. Do not include placeholder text such as `TODO`.

Self-check before answering:
- The JSON is valid.
- Unknown fields are absent.
- Root localized metadata is absent.
- All locale keys in `text.translations` are non-empty BCP 47 tags.
- All translation values are non-blank.
- The JSON is pretty-printed, not minified.
- Segment cues respect the short-cue limit.
- Longer rationale appears only at workout or repeat-block scope.
- Rider-facing copy does not mention authoring/meta concepts such as `.ewo` or "repeat block".
- `messages` do not encode hidden execution rules.
- Any HR target includes complete `control`.

Workout brief:
<PASTE_WORKOUT_BRIEF_HERE>

Requested translation locale tags:
<PASTE_LOCALES_HERE>

Extra constraints:
<PASTE_OPTIONAL_CONSTRAINTS_HERE>
```

## Review Notes

When reviewing the generated workout, check these first:

- root metadata stays unlocalized in canonical `.ewo` `1.5`
- that `1.5` root-metadata limitation is intentional for this workflow and does
  not contradict the repository's broader `.ewo` `1.6` parser/spec support
- the JSON is formatted like a checked-in repo asset, not minified into one line
- the same explanation is not repeated at workout, repeat, and segment scope
- segment cues are readable at a glance on a small screen
- rider-facing copy never talks about `.ewo`, JSON structure, or "repeat blocks"
- translation keys stay stable and producer-friendly
