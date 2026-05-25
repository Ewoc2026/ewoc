# Contributing

Thanks for helping improve Ewoc.

## Scope

The first public repository is intentionally conservative. Good early
contributions are small, testable fixes to:

- Android build and runtime correctness
- desktop editor build and editing behavior
- EWO parsing, validation, schema, and fixtures
- documentation that helps users build or understand the project

Large product changes, new cloud services, paid-feature models, or trainer
control policy changes should start as an issue before a pull request.

## Workflow

1. Create a branch from `main`.
2. Keep the change focused on one task.
3. Add or update tests when behavior changes.
4. Update docs when build, validation, privacy, or user-facing behavior changes.
5. Open a pull request with the validation commands you ran.

## Local Validation

Run the smallest checks that cover your change. Useful defaults:

```bash
./scripts/gradle-safe.sh :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false
./scripts/gradle-safe.sh :apps:desktop:compileKotlin --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false
./scripts/gradle-safe.sh :app:testDebugUnitTest --no-daemon --rerun-tasks --no-configuration-cache -Dkotlin.incremental=false
git diff --check
```

Run Gradle commands sequentially in one checkout. Parallel Android/desktop
builds can contend on Kotlin caches and produce misleading failures.

Trainer-sensitive behavior needs real hardware validation before it is treated
as done.

## Style

- Use English for comments and documentation.
- Prefer KDoc for public classes and functions when behavior, invariants, or
  edge cases are not obvious.
- Keep comments focused on why something is true, not what the code already
  says.
- Follow existing Kotlin, Gradle, Compose, and documentation patterns.
- Keep unrelated refactors out of focused fixes.

## Pull Request Checklist

- [ ] Scope is limited to one clear task.
- [ ] Relevant local validation passes.
- [ ] User-facing changes update docs or changelog when appropriate.
- [ ] UI changes include screenshots or a clear validation note.
- [ ] Trainer-sensitive changes describe real hardware coverage or remaining
      hardware risk.
