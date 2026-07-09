# Ewoc Architecture

## Purpose
This document explains the project structure for contributors who want to reuse this repository as a starting point.

## EWO Module Boundary

The canonical EWO specification workspace at `spec/ewo/` plus
`:modules:ewo-core` are the stable public format boundary for governance and
interoperability.

`:modules:ewo-core` also hosts a public-side `.zwo` importer
(`com.ewo.core.zwo.importZwo`) that converts Zwift workout XML to canonical
EWO domain objects. This belongs to the public boundary because `.zwo` import
is an interoperability concern, not an app-specific feature — any tool or
client consuming EWO should be able to import `.zwo` files.

Ewoc-specific Android document flow, import sniffing, runtime execution, BLE,
release packaging, and the current JVM validator bridge under
`:tools:ewo-validator-cli` remain app-owned concerns outside that format
boundary, as do the higher-level authoring layers under
`:modules:ewo-editor-*` and `:apps:desktop`. The desktop host is the authoring
shell for the shared editor stack: chart-first workout authoring, localized
metadata/message editing, preview-only rider profile inputs, and denser
tree/inspector tooling. Canonical root-tag policy still comes from
`:modules:ewo-core`: the shared facade exposes authoring-facing tag validation
and intake sanitization so desktop editing can reject invalid ASCII slug tags
early while still recovering malformed saved root tags on open.

The canonical `.ewo` minor-version line is also intentionally separate from any
public parser/validator release numbering. Spec versions describe the authored
file contract, while public module releases should publish an explicit
supported-format matrix instead of implying numeric lockstep.

See `docs/trainer-variability-strategy.md` for the current FTMS/trainer
integration posture: explicit protocol state machines, app-owned logical ride
continuity, conservative fallbacks, stable observability, and room for
trainer behavior profiles.

## Runtime Layers

### 1) UI and Navigation Layer
- `app/src/main/java/io/github/ewoc2026/ewoc/EwocApplication.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/MainActivity.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/MainActivityDocumentPickerLaunchGate.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/MainActivityDocumentFlowCoordinator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/MainActivityEwoDocumentCoordinator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/DebugAutomationCommand.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/MainActivityDebugIntentCoordinator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/MainActivityLifecycleCoordinator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/MainActivityPermissionCoordinator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/MainActivityScreenWakeCoordinator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/MainActivitySummaryFitShareCoordinator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/MainActivitySystemUiCoordinator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/PrivacyPolicyLinkLauncher.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/ewoeditor/EwoEditorCoordinator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/ui/MainActivityUiModelFactory.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/ui/MainActivityContent.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/ui/EwoEditorScreen.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/ui/EwoEditorScreenPreviews.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/ui/Screens.kt`

Responsibilities:
- Provide the app-level Android startup contract, including the release/minified
  app initialization path.
- Emit one startup `APP_BUILD` log line from `EwocApplication` so manual FTMS validation can
  confirm the installed build's version, branch, git SHA, and current
  worktree-dirty marker before interpreting BLE behavior.
- Keep the rider/operator-facing session debug explanation sourced from one
  semantic seam instead of letting overlay copy and adb `dump_status`
  summaries drift. `MainViewModel` now forwards a shared
  `Intent / Policy / Why / Next` explanation from `SessionOrchestrator` into
  both the compact overlay diagnostics text and the automation status dump, so
  "what workout behavior does the app think it currently owes the user?" is
  visible without reconstructing that from raw flags.
- Keep the session debug probe overlay auto-show policy tied to rider-visible
  session readiness (`SESSION` + FTMS ready) instead of live pedaling
  telemetry, because the overlay is a communication surface rather than a
  trainer-command gate.
- Keep trainer-release diagnostics centralized in `SessionOrchestrator`: the
  same release snapshot now feeds `UI_DUMP`, adb `dump_status`, the structured
  `trainer_release_runtime` diagnostics category, and the compact probe-overlay
  `Release:` line so future teardown-policy refactors do not drift across
  separate ad hoc status strings. That same snapshot now also surfaces both
  `summaryWindowOpen` and `restartWindowOpen`, so the app can separate
  "SUMMARY may finish now" from the stricter "Continue ride may reconnect now"
  without inventing two disconnected visibility channels.
- Render top-level destinations (`MENU`, `CONNECTING`, `SESSION`, `STOPPING`, `SUMMARY`).
- Forward user intents to `MainViewModel`.
- Keep UI mostly stateless by consuming immutable UI models.
- Keep MainActivity-side document launch/save fallback policy in
  `MainActivityDocumentFlowCoordinator` so the hidden legacy workout-editor
  export path, canonical workout-editor save, and summary FIT export do not
  drift back into inline Compose callback branches.
- Keep SAF picker launch reentrancy policy in `MainActivityDocumentPickerLaunchGate` so repeated taps
  or repeated callback paths cannot stack multiple system document pickers on top of each other while
  the Activity still has an earlier picker request in flight. That Activity-owned seam intentionally
  stays narrower than a global UI debounce rule: only system document launchers share the gate, and
  any picker result (including cancellation) releases it immediately.
- Keep summary FIT chooser launch reentrancy in `MainActivitySummaryFitShareCoordinator` so repeated
  taps cannot launch multiple external chooser/share flows before the app regains focus. That gate is
  intentionally released from `MainActivity.onResume()`, because external chooser/share flows do not
  provide the same result-callback seam that SAF launchers do.
- Keep EWO SAF open/save result policy in `MainActivityEwoDocumentCoordinator` so UTF-8 reads,
  filename fallback, and save-success-only completion stay testable outside Activity Result callbacks.
- Keep debug-only adb intent parsing/execution dispatch in `MainActivityDebugIntentCoordinator`
  so lifecycle hooks do not duplicate request-control validation, generic
  debug automation, and mock-trainer automation routing.
- Keep `MainActivity` in `singleTop` launch mode so repeated adb automation
  commands reach the existing top instance through one predictable
  `onNewIntent()` path during validation loops.
- Keep the typed debug command vocabulary in `DebugAutomationCommand` so
  menu-step jumps, documents-folder workout selection, imported-HR validation
  prep, direct trainer warm-prepare/release requests, direct mock-trainer
  enable/disable toggles, baseline-screen open/start/back actions,
  post-workout freeride handoff requests, setup-state dumps, and other adb
  shortcuts stay explicit and testable instead of
  drifting into ad-hoc extra parsing inside `MainActivity`.
- Keep live-riding safety probes AI-mediated: session-screen debug buttons may
  report only rider observations back into `MainViewModel`, while trainer
  commands and rider instruction text still flow through the typed
  `DebugAutomationCommand` surface. That split prevents the emergency/debug UI
  from becoming an unreviewed second command path into FTMS control.
- Keep one shown probe equal to one decisive rider answer: once the rider has
  answered, both the overlay and `MainViewModel` now lock that probe instance
  against follow-up taps until the operator hides or re-shows it. That avoids
  mixing pre- and post-transition feedback into the same evidence point.
- Keep the current probe overlay's lifecycle explicit in design discussions:
  the live 2026-03-26 disconnect matrix showed that
  `disconnect_trainer_transport` moves the app to `MENU` quickly enough that
  the original `SESSION`-scoped rider probe could not capture
  post-disconnect feedback. `MainActivityContent` now mirrors that same probe
  state outside `AppScreen.SESSION`, but live disconnect retests must still
  confirm the cross-screen render behaves as intended before the team treats
  post-disconnect rider signaling as fully solved.
- Keep prepared `Continue ride` handoff from clearing active probe state just
  because it internally reuses a MENU reset: `MainViewModel` now preserves an
  armed or visible probe across that continuation-only menu hop while the
  handoff overlay is active, so generic menu cleanup does not defeat the
  shared top-level probe host during live rider validation.
- Keep live probe timing app-driven instead of countdown-driven whenever the
  rider must walk back to the trainer: `MainViewModel` can now arm a session
  debug probe and auto-show it as soon as the app is on `SESSION` and FTMS is
  ready, which is a much stronger readiness signal than chat timing or fixed
  adb delays. The
  same gate is now evaluated through one shared policy seam, so runtime status
  dumps and unit tests describe the same blocker instead of relying on log
  interpretation.
- Keep post-workout continuation validation anchored on stable
  `TEST_MARKER` breadcrumbs at both the rider-action layer and the
  trainer-protocol layer, so Samsung/tablet live tests can prove whether
  hidden exit prep really started before `Continue ride` was pressed.
- Keep root Compose callback forwarding explicit inside
  `ui/MainActivityContent`, because missing pass-through at that seam can make
  production behavior diverge from lower-level session/orchestrator tests even
  when the underlying handoff logic is correct.
- Keep repeatable trainer-proof loops in dedicated adb scripts such as
  `scripts/adb/warm-connection-validation.sh` and
  `scripts/adb/run-baseline-takeover-validation.sh` so release-prep validation
  logic does not keep leaking into app runtime behavior once the needed debug
  intents exist. The baseline helper now also owns screenshot/log/report
  bundling so reviewer-facing evidence stays script-level too, including
  baseline and cleanup screenshots plus optional review-context metadata in the
  generated report.
- Keep Activity teardown policy in `MainActivityLifecycleCoordinator` so configuration-change
  unbinds and finishing-session shutdown rules stay testable outside `ComponentActivity.onDestroy()`.
- Keep Activity-side runtime permission launch policy plus app-settings
  recovery in `MainActivityPermissionCoordinator` so Bluetooth permission
  gating and package-scoped Settings recovery stay testable outside the
  launcher callbacks.
- Keep Activity-side keep-awake window-flag policy in `MainActivityScreenWakeCoordinator` so
  session-driven `FLAG_KEEP_SCREEN_ON` activation/release no longer drifts back into inline
  `MainActivity` methods.
- Keep screen-specific system-UI policy in `MainActivitySystemUiCoordinator` so immersive
  navigation-bar hide/show behavior no longer branches inline inside the Compose `LaunchedEffect`.
- Keep summary FIT chooser launch plus launch-failure reporting in
  `MainActivitySummaryFitShareCoordinator` so the last Activity-side share branch does not drift
  back into inline Compose callback code.
- Keep privacy-policy browser launch policy in `PrivacyPolicyLinkLauncher` so
  external guidance links reuse one canonical URL source and one
  failure-handling seam.
- Keep Android EWO editor command history, preview recomputation, and status/file-name state in
  `EwoEditorCoordinator`, while `EwoEditorScreen` remains a mostly stateless Compose shell over
  immutable snapshots. The current Android shell exposes canonical v1.5 segment label/note,
  repeat-count, and free-ride cadence editing without letting partial cadence text write `0`
  placeholders back into the shared document mid-edit. Athlete-profile inputs plus steady/ramp/
  free-ride duration fields, steady power watts, and repeat counts also keep partial text locally
  until a valid commit is ready, so invalid intermediate edits no longer snap back to the last
  parsed document value. Small helper seams now keep label/note blank normalization plus optional/
  required integer and positive repeat-count command resolution JVM-testable without introducing a
  separate Android instrumentation harness on this branch. Root localized metadata from canonical
  `.ewo` `1.6` is still outside the current Android editor authoring surface, so Android keeps the
  existing plain-string root title/description editing model while parser/import flows preserve the
  newer metadata when present. The bottom action surface now also applies navigation-bar padding so
  edge-to-edge tablet runs do not hide `New` / `Open` / `Save` under the system bar during manual
  export validation. Steady segments now mirror ramp editing by offering a watts/`% FTP` target
  mode toggle, including steady children selected from repeat blocks, and the Android editor chart
  now colors bars from FTP-relative zone semantics instead of normalizing colors against the chart's
  local max value.
- Keep top-level UI-model assembly in `MainActivityUiModelFactory` so `MainActivity` does not rebuild the
  full `MainActivityUiModel` / `MenuUiState` graph inline whenever those models evolve.
  The factory now also exposes a snapshot-driven seam so AI-routing and connection-state derivation stay
  unit-testable without rebuilding `MainViewModel`. The same seam now carries telemetry-only session
  duration from `SessionState.durationSeconds` into `SessionScreen`, so workoutless rides can render a
  live open-ended elapsed timer without reviving stale runner labels from an old structured workout.
  It now also carries `postWorkoutFreerideModeActive`, which lets the session UI and lifecycle
  read model distinguish rider-controlled continuation after a finished workout from both
  telemetry-only rides and structured control-seeking sessions. It also now
  owns the post-workout continuation screen override, so the one-button
  `Continue ride` handoff can stay visually on `SESSION` while orchestration
  briefly passes through internal `MENU` / `CONNECTING` states.
- Keep workout-profile ramp rendering consistent across Android surfaces in
  `ui/components/WorkoutProfileChart.kt`, where both the full session/menu
  chart and the lightweight Android editor preview now share the same
  multi-slice ramp coloring helper instead of rendering ramps with one average
  zone color.
- Keep the tablet-landscape telemetry chart constrained by the outer session
  layout's actual chart budget: `SessionDestinationScreen` owns the fixed
  viewport sizing for that branch, `WorkoutProgressSection` subtracts selector
  space before passing the budget down, and `ui/components/LiveTelemetryChart`
  must fit its lane stack inside that explicit height instead of assuming it
  can always expand to its preferred total height.
- Keep post-workout handoff completion logic tied to the new session's real
  readiness signals instead of the stale structured-workout runner snapshot:
  telemetry-only continuation can legitimately keep `runner.done == true`
  while FTMS is already reconnected and controllable again.
- Keep live rider-to-AI communication during trainer safety work inside a
  dedicated session debug probe overlay: `SessionRuntimeUiState` carries its
  state, `MainActivityUiModelFactory` forwards it, `SessionScreen` still owns
  the in-session presentation, `MainActivityContent` now mirrors the same
  overlay after top-level fallback, and `MainViewModel` logs the resulting
  rider signals as `TEST_MARKER` events.
- Keep the same probe state queryable without logcat timing assumptions:
  `SessionRuntimeUiState` now also stores the last received signal label,
  count, and timestamp, the overlay renders a local receipt line for the
  rider, and `dump_status` exposes the same fields plus armed-probe state and
  the current arm-gate blocker for operator-side polling.
- Keep duplicate rider taps observable but non-authoritative: follow-up taps
  on an already-answered probe now produce ignored-signal breadcrumbs instead
  of mutating the accepted evidence for that prompt.
- Keep that overlay's copy on explicit theme-contrast colors rather than
  implicit defaults. Live trainer testing can happen in either light or dark
  mode, so the rider must be able to read probe instructions at a glance.

### 2) ViewModel and App State Layer
- `app/src/main/java/io/github/ewoc2026/ewoc/MainViewModel.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/DocumentsFolderUiState.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/DebugAutomationUiState.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/ActivityCallbackBridge.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/AiAssistantUiState.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/ConnectionRecoveryUiState.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/ProfileSettingsUiState.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/SessionRuntimeUiState.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/SessionStartEligibilityUiState.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/SummaryFitUiState.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/WorkoutSelectionUiState.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/DeviceScanUiStateAdapter.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/DeviceSelectionFacade.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/MenuStatusProbeFacade.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/MenuStatusProbeStateAdapter.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/DocumentsFolderImportCoordinator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/SafUtf8TextWriter.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/CompatibilityCheckLaunchCoordinator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/CompatibilityCheckCoordinator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/CompatibilityCheckRunFacade.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/CompatibilityModeUiState.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/SummaryExitStateAdapter.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/SummaryFitAutoExportCoordinator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/SummaryExitCoordinator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/SummaryFitExportCoordinator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/SummaryFitExportPreferenceCoordinator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/SummaryFitShareCoordinator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/AiAssistantFacade.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/AiAssistantIntegration.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/baseline/BaselineFitnessTestModels.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/baseline/BaselineFitnessTestProtocol.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/baseline/BaselineFitnessTestStateMachine.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/baseline/BaselineFitnessTestResultCalculator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/baseline/BaselineFitnessTestCoordinator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/baseline/BaselineFitnessTestSettingsStorage.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/baseline/BaselineFitnessTestResultPromotionCoordinator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/baseline/BaselineFitnessZoneCalculator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/AppUiState.kt`

Responsibilities:
- Own long-lived app/session services across configuration changes.
- Hold user-selected device/workout/FTP state.
- Keep Activity-bound permission launchers and screen-on hooks in
  `ActivityCallbackBridge` so configuration-change rebinding does not drift back into
  another loose `MainViewModel` callback cluster. The bridge no longer exposes
  a current-Activity callback because the free/public branch has removed the
  temporary billing shim as well as active Play Billing architecture.
- Keep the product-facing `Continue ride` hard-cutover in `MainViewModel` as a
  polling coordinator over existing summary/menu navigation instead of pushing
  restart timing heuristics into Compose. The current branch now waits until
  `SessionOrchestrator` reports that the prior FTMS link has both disconnected
  and cleared the trainer-settle window before it auto-starts the fresh
  telemetry-only continuation, which preserves the one-button UX while giving
  trainer firmware the mode-exit dwell it appears to require.
- Keep explicit-close teardown honest about whether a disconnect callback can
  still arrive. The current branch now lets `FtmsBleClient.close()` report
  whether an active GATT existed, so `SessionOrchestrator` can synthesize the
  disconnect-complete path when stop-flow or warm-release teardown has no live
  transport left to close instead of wedging the settle-window gate forever.
- Keep logical cumulative trainer totals in `SessionState` as well as in
  export-only summary data, so live session surfaces can keep showing the
  merged `distance` / `kcal` values after a continuation cutover even if the
  new trainer-side FTMS session restarts its raw counters from zero.
- Keep the workout-complete prompt as the visible one-step decision surface
  while `SessionOrchestrator` now pre-arms trainer exit underneath it. That
  separation lets the rider's decision time double as the trainer's required
  stop/disconnect dwell window and gives `Continue ride` a faster in-place
  continuation path when the trainer is already prepared.
- The free/public migration branch has removed the Play Billing dependency,
  paywall UI, paywall analytics, paywall strings, debug entitlement override,
  and temporary no-Play billing shim from Android. App features now resolve
  directly as available in the free/public product model.
- Keep FTP and HR-profile input, validation, and persisted selection state in
  `ProfileSettingsUiState` so menu profile-setting callbacks, restore logic, and session/AI
  consumers reuse one owner instead of scattering profile fields back across `MainViewModel`.
- The free/public migration branch removes Android AI workout generation rather than
  replacing it with another backend-backed or quota-backed feature. The app no longer has
  the AI setup step, generation endpoint build config, generation coordinator/facade,
  backend AI workout client, quota/debug fake-response UI, or generated-workout handoff.
- Keep baseline fitness test protocol math, latest-only metadata persistence, FTP provenance,
  runtime orchestration, and result-promotion ordering in `baseline/` so future runtime/UI work
  can reuse one seam instead of leaking baseline-specific rules into `SessionOrchestrator` or
  duplicating FTP-source handling inside ad hoc menu callbacks.
- Keep baseline-test trainer preparation and PRECHECK waiting policy behind the
  `BaselineFitnessTestRuntimePort` seam so the dedicated baseline screen can self-connect through
  the FTMS/mock stack without reusing the workout session's `CONNECTING` screen or duplicating
  transport/polling rules inside `MainViewModel`, while still releasing borrowed transport
  deterministically on cancel/back exit. The same seam now also ensures warm-up time does not
  start burning before the rider produces the first active cadence/power signal, even if ERG
  control was already granted. When the user leaves the baseline screen, the runtime state is
  also reset back to `IDLE` so immediate retries do not reopen on stale terminal output.
- Keep FTMS device-selection side effects in `DeviceSelectionApplyCoordinator` so trainer changes
  consistently cancel stale probe scans, release any previous MENU-time warm trainer preparation,
  persist the new device identity, and request one fresh warm-link attempt plus one fresh passive
  availability probe in a deterministic order.
- Keep connect-timeout messaging plus menu recovery prompt guidance in
  `ConnectionRecoveryUiState` so rollback-to-menu recovery, summary-exit cleanup, and
  prompt follow-up actions reuse one owner instead of drifting back into another loose
  `AppUiState` / `MainViewModel` recovery cluster.
- Keep phase-scoped AI assistant message, error, and MENU template-key state in
  `AiAssistantUiState` so `AiCoordinator`, `AppUiState`, and activity-level
  screen routing reuse one owner instead of drifting that cluster back inline into
  `AppUiState`.
- Keep selected workout/import payload, derived workout metadata, import failure state,
  and execution-mode banner in `WorkoutSelectionUiState` so session orchestration,
  local assistant context, and menu/session UI consumers reuse one owner instead of drifting
  that cluster back inline into `AppUiState`. The same owner now also keeps the shared
  `SessionSetupMode` source of truth so setup selection, start gating, orchestration,
  and summary rendering stay aligned when the user selects `Telemetry only`. Telemetry-only
  now takes precedence over any stale structured workout payload, so menu/session surfaces
  and cadence gating must ignore leftover editor/file metadata instead of reviving a runner.
- Keep FTMS readiness/control, last target power, workout-ready, stop-flow, and pending
  start/cadence runtime flags plus latest live telemetry timestamps in `SessionRuntimeUiState`
  so `AppUiState`, baseline-test runtime orchestration, session-lifecycle derivation, and session
  orchestration consumers reuse one owner instead of drifting back into another loose runtime flag
  cluster. The same runtime state now also owns `postWorkoutFreerideModeActive`, which keeps
  post-workout rider-controlled continuation explicit instead of overloading telemetry-only state
  or stale completed-runner flags. The newest release-safety foundation also keeps
  `trainerControlAuthority` plus `lastAppControlledTargetPower` there, so release
  policy can reason about app-owned load separately from the older UI/debug
  `lastTargetPower` field. That same split now also protects direct
  telemetry-only starts after a previous structured workout: if telemetry-only
  is selected while app-controlled target history is still present,
  `SessionOrchestrator` forces a clean FTMS disconnect/reconnect boundary and
  starts the new session without re-requesting control, because some trainers
  do not release rider-local manual adjustment until the old app-owned
  control epoch has actually ended.
- Keep baseline-screen trainer telemetry consumption compatible with mock mode by letting the
  session runtime expose external trainer-preparation state and screen-agnostic mock telemetry
  routing, so baseline start can borrow FTMS readiness/power flow and hand that ownership back on
  menu return without silently depending on `AppScreen.SESSION`.
- Keep one-shot debug automation flags in `DebugAutomationUiState` so the pending mock-trainer
  scenario and forced Documents-folder write-failure override share one owner instead of
  drifting back into ad hoc `MainViewModel` vars. The same debug path now also exposes warm-link
  ownership, preparation state, prepared trainer MAC, and reuse readiness through `dump_status`,
  which keeps repeated FTMS validation loops observable without adding release-facing UI. The same
  status dump now also exposes explicit runner/freeride fields so adb validation can poll for a
  post-workout handoff without scraping the raw `RunnerState` string. The same
  debug surface now also includes `force_clean_menu_reset`, which is
  intentionally stronger than `back_to_menu`: it clears session, summary, and
  workout remnants so manual validation can always return to a deterministic
  MENU baseline without losing durable device or Documents-folder selections.
  The same debug surface now also includes direct FTMS probe commands
  (`request_trainer_control`, `set_trainer_power`,
  `disconnect_trainer_transport`, `stop_trainer_workout`, `reset_trainer`) so
  protocol-sensitive investigation
  can compare trainer behavior through a warmed menu transport or an active
  session transport without rebuilding a separate UI automation path for every
  command variant. `clear_trainer_power` now remains only as a legacy alias
  for `set_trainer_power --ei target_watts 0`, because the previous naming
  falsely implied that the app already had a distinct FTMS clear/release path.
  FreeRide and baseline "exit ERG" flows now also route through explicit FTMS
  `Reset` semantics instead of through a zero-target abstraction, which keeps
  product/runtime intent aligned with the public FTMS control-point model.
- Centralize Documents-folder access/import state plus staged summary FIT export state in
  `DocumentsFolderUiState` so folder bind/import and FIT export/share flows reuse one owner.
- Keep summary FIT preference state and the transient one-shot auto-export fingerprint inside
  `SummaryFitUiState` so summary preference, auto-export gating, and summary-exit reset logic
  reuse one owner instead of scattering summary-only state back across `MainViewModel`.
- Keep the multi-owner summary-exit cleanup adapter in `SummaryExitStateAdapter.kt` so
  `SummaryExitCoordinator` can reset summary, FIT export, compatibility prompt, and
  connection-timeout state without rebuilding another anonymous bridge inside `MainViewModel`.
- Keep workout-editor draft, source XML, validation surfaces, status banner, and save/apply
  continuation state inside `WorkoutEditorSessionUiState` so editor coordinators reuse one
  owner instead of reintroducing another loose `MainViewModel` workflow cluster.
- Keep debug mock-trainer mode plus the selected FTMS/HR identities used by session-start
  guards inside `SessionStartEligibilityUiState` so restore logic, picker selection, and
  start-gating consumers reuse one owner instead of drifting into separate `MainViewModel`
  vars.
- Delegate picker scan and selection flow through `DeviceSelectionFacade` to preserve
  callback ordering while reducing direct scan orchestration in `MainViewModel`.
- Keep the picker scan-state adapter in `DeviceScanUiStateAdapter.kt` so
  `DeviceScanCoordinator` can mutate picker visibility, scan status, stop-button
  gating, and the shared scanned-device list without rebuilding another anonymous
  bridge inside `MainViewModel`.
- Delegate menu-only device availability probing through `MenuStatusProbeFacade` so
  passive scan lifecycle can evolve without inflating `MainViewModel`.
- Keep the menu-only probe state adapter in `MenuStatusProbeStateAdapter.kt` so
  `MenuStatusProbeCoordinator` can read current screen/selection state and mutate
  FTMS/HR reachability counters without rebuilding another anonymous bridge inside
  `MainViewModel`.
- Delegate documents-folder bind/import workflow through `DocumentsFolderImportCoordinator`
  so SAF permission fallback, folder workout discovery, and folder-picked import routing
  stay aligned without re-branching binder/list/import state transitions inline in
  `MainViewModel`.
- Keep the shared SAF UTF-8 document writer in `SafUtf8TextWriter.kt` so workout-editor
  document export/save flows and the canonical EWO save flow reuse one output-stream
  write contract instead of duplicating encoding and mode details inline in
  `MainViewModel` and `MainActivity`.
- Delegate Compatibility Mode launch preflight and execution-request assembly through
  `CompatibilityCheckLaunchCoordinator` so missing-trainer and permission guards stay
  aligned with the exact request payload that reaches the background executor.
- Delegate Compatibility Mode run start/reset policy and completion status routing
  through `CompatibilityCheckCoordinator` so compatibility prompts, latest run
  artifacts, and completion cleanup stay aligned without branching
  pass/fail/persist outcomes inline in `MainViewModel`.
- Delegate Compatibility Mode background scheduling and main-thread completion handoff
  through `CompatibilityCheckRunFacade` so launch gating, worker submission, and
  closed-ViewModel completion suppression evolve behind one durable seam instead of
  being nested inline in `MainViewModel.onRunCompatibilityCheckRequested()`.
- Keep the Compatibility Mode FTMS control contract in `CompatibilityCheckOrchestrator`
  so any successful optional reset probe is followed by a fresh request-control step
  before target-power commands resume; this avoids leaking trainer-specific ownership
  assumptions into `FtmsCompatibilityDeviceGateway` or `MainViewModel`.
- Keep Compatibility Mode mutable UI/workflow state inside `CompatibilityModeUiState`
  so the run-launch and debug diagnostics ports mutate the same backing state
  without forcing `MainViewModel` to keep another composite compatibility/debug
  bridge inline.
- Delegate one-shot summary FIT auto-export gating through `SummaryFitAutoExportCoordinator`
  so preference checks and summary fingerprint deduplication stay testable without moving
  Compose-owned summary state out of the ViewModel layer.
- Delegate summary exit reset policy through `SummaryExitCoordinator` so pending summary-only
  export state, compatibility prompts, and summary AI cleanup stay testable without mixing
  reset branches into `MainViewModel.onBackToMenu()`.
- Delegate summary FIT snapshot staging and Documents-folder tree-write routing through
  `SummaryFitExportCoordinator` so SAF fallback policy, explicit create-document completion,
  and final status mapping stay testable without moving pending export state out of the
  ViewModel layer.
- Delegate summary FIT export preference persistence and acknowledgement messaging through
  `SummaryFitExportPreferenceCoordinator` so preference/status policy stays testable without
  branching inline in `MainViewModel`.
- Delegate summary FIT share-intent preparation and launch-failure status mapping through
  `SummaryFitShareCoordinator` so cache export outcomes and summary status policy stay
  testable without duplicating result branches in `MainViewModel`.
- Delegate workout-editor imported-workout hydration and fresh-draft reset policy through
  `WorkoutEditorHydrationCoordinator` so imported workout selection, editor draft state,
  source diagnostics, and save/apply continuation cleanup stay aligned without re-branching
  those transitions inline in `MainViewModel`.
- Delegate workout-editor workflow policy through `WorkoutEditorFlowCoordinator` so
  editor entry, save-before-apply prompt handling, validation acknowledgement, and
  menu-return routing stay testable without mixing navigation/prompt policy into
  the lower-level draft/source-sync seam.
- Delegate workout-editor draft/source synchronization through
  `WorkoutEditorDraftCoordinator` so mode switches, XML validation, structured
  draft mutation, and step-list edits keep one source-of-truth invariant instead
  of re-branching draft/source updates inline in `MainViewModel`.
- Delegate workout-editor persistence/apply policy through
  `WorkoutEditorPersistenceCoordinator` so structured/source workout resolution,
  validation failure status mapping, and apply-to-menu side effects stay aligned
  without splitting prompt handling from persistence state.
- Delegate workout-editor save target orchestration through `WorkoutEditorSaveCoordinator`
  so Documents-folder routing, explicit create-document completion, and post-save
  apply/status policy stay testable without re-branching save outcomes inline in
  `MainViewModel`.
- The free/public migration branch removes user-visible support-bundle export
  and backend upload flows. Compatibility Mode and session diagnostics remain
  local runtime/debug signals only; they no longer stage zip exports or enqueue
  ingest work.
- Keep AI screen-phase mapping, reachability translation, Android string formatting,
  and ViewModel-local AI integration assembly in `AiAssistantFacade.kt` and
  `AiAssistantIntegration.kt` so `AiCoordinator` can stay focused on recommendation
  lifecycle/policy orchestration while `MainViewModel` stops carrying AI-specific
  helper glue and composition branches inline. The free/public branch removes
  Android AI workout generation transport and Health Connect ingestion, so this
  layer now evaluates local recommendation context without a backend workout
  generation route or wearable-data permission flow.

### 3) Session Orchestration Layer
- `app/src/main/java/io/github/ewoc2026/ewoc/session/SessionIntentDiagnostics.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/session/SessionOrchestrator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/session/release/ReleaseRampDecider.kt`

Responsibilities:
- Coordinate FTMS lifecycle, workout lifecycle, and UI navigation.
- Enforce start/stop state transitions and recovery behavior.
- Connect callback-driven BLE events to deterministic UI/session transitions.
- Keep session-intent diagnostics semantic and shared:
  `SessionIntentDiagnostics` translates runtime facts such as setup mode,
  release state, control authority, and prepared-link reuse into one stable
  operator explanation (`Intent`, `Policy`, `Why`, `Next`) so debug overlay
  text and `dump_status` describe the same current contract instead of
  drifting into separate ad hoc strings.
- Keep trainer-release authority and future release-ramp policy as a dedicated
  seam outside the main orchestrator flow: successful app-owned target writes
  promote runtime authority to `APP_CONTROLLED`, disconnect clears that
  authority, and `ReleaseRampDecider` owns the pure decision model that now
  guides post-workout `Continue ride` teardown instead of leaving that policy
  scattered across ad hoc branches. The first trainer-profile foundation
  slice now also names the current Tunturi-proven tuning explicitly through
  `TrainerReleaseProfile` / `TrainerReleaseProfiles.tunturiBaseline`, so one
  trainer-specific policy set is no longer presented as an unnamed generic
  default. The next cleanup then grouped that release policy into three
  explicit concerns: rider-safety ramp rules, the app-requested low-load
  release target, and trainer control-release semantics.
- Keep release-ramp observability structured from the start: the decider now
  emits stable `evaluate` / `decision` trace payloads through a dedicated
  emitter seam, and `SessionOrchestrator` keeps recording those breadcrumbs
  under diagnostics category `release_ramp` even while the same policy now
  schedules the pre-disconnect target ramp for `Execute` outcomes.
- Keep trainer-release runtime observability beside the pure decider output:
  `SessionOrchestrator` now publishes one current-state release snapshot for
  `UI_DUMP`, adb `dump_status`, and the debug probe overlay, while
  `trainer_release_runtime/*` diagnostics log control-request, ramp,
  soft-stop, explicit-close, and reconnect milestones. That snapshot also
  makes the current implementation assumption explicit by showing the active
  policy intent (`CONTINUE_RIDE_HANDOFF`) even when the product question is
  whether `SUMMARY` should keep inheriting that path. The latest slice now
  also gives `SUMMARY` its own readiness seam: `MainViewModel` still waits for
  `restartWindowOpen` only when the rider chose `Continue ride`, while the
  prepared completion-to-summary path can finish as soon as the hidden
  completion exit has passed ramp/request-control/STOP and reached the
  app-owned explicit-close phase. The follow-up slice then narrowed the
  product rule further: ordinary `SUMMARY` no longer starts that hidden
  disconnect-aware prep when the workout-complete dialog first appears.
  `Continue ride` now requests the stricter release prep only after the rider
  actually taps that choice, while normal summary falls back to the standard
  stop-to-summary path. Real-trainer proof on 2026-03-28 already confirmed
  that `summaryWindowOpen` was sufficient before the later disconnect/settle
  work completed. The earlier 2026-03-29 follow-up then showed that another
  normal-summary disconnect path still remains after this choice-aware split:
  ordinary `SUMMARY` still logged
  `completion_exit_prep_requested -> teardown_decision_applied ->
  release_ramp_started -> completion_exit_explicit_close_started ->
  explicit_close_disconnect_observed` on the real trainer. The newest code
  slice now splits that deeper path at the shared stop-flow close step:
  workout-complete `SUMMARY` uses a small keep-connected stop-flow policy,
  while `Continue ride` and the prepared handoff path still retain the
  stricter disconnect-aware teardown. A same-day follow-up then showed that
  the first apparent trainer "regression" was actually a stale local APK:
  the installed artifact predated the latest `MainViewModel` source edits.
  After rebuilding and reinstalling the current dirty-worktree APK, the real
  trainer finally matched the intended architecture seam: ordinary
  workout-complete `SUMMARY` emitted only the visible
  `workout_complete_dialog_presented` and `workout_complete_summary_tapped`
  markers, reported `preparedExitWindow=false`, and produced no
  `trainer_release_runtime/*`, `completion_exit_*`, or `explicit_close_*`
  teardown events. The next live product question is therefore no longer
  normal-summary teardown correctness; it is how the on-demand
  `Continue ride` delay feels now that release starts only after the tap. The
  newest observability follow-up also adds one shared semantic explanation of
  current session intent on top of the lower-level release snapshot, so
  overlay readers and `dump_status` consumers can tell whether the app thinks
  it is in telemetry-only, directed-workout, or continuation policy without
  reverse-engineering that from separate flags. The newest local-only
  follow-up then targets that transition feel directly without reopening the
  clean `SUMMARY` seam: release-ramp plans can hold the final floor target
  briefly before `STOP` / disconnect, and the runtime diagnostics now expose
  both `floorHoldMs` and `release_ramp_floor_hold_active` so the next
  real-trainer pass can tell whether the trainer received one short settled
  low-load window before the stricter teardown continued. That next
  real-trainer pass now also exists: the rebuilt-app branch finally recorded
  a rider `Smooth` answer for `Continue ride`, while runtime events showed
  `release_ramp_started ... floorHoldMs=500`,
  `release_ramp_completed elapsedMs=3501 targetPowerW=60`, then
  `soft_stop_started`. A same-session diagnostics-only follow-up then fixed
  one smaller observability edge case so `release_ramp_floor_hold_active`
  logs on first entry into the hold phase even if scheduler drift lands after
  the exact `durationMs` boundary. The latest local-only follow-up now keeps
  that timing profile but lowers the actual release floor to `25W` through a
  separate duration-reference seam, so the next live retest can answer one
  narrower question: does a lower final unload still feel acceptable when the
  ramp timing itself is intentionally held constant? That retest now also
  passed on the real trainer: the rider again answered `Smooth`, and runtime
  evidence showed `170W -> 25W`,
  `release_ramp_floor_hold_active`, then
  `release_ramp_completed elapsedMs=3502 targetPowerW=25 floorHoldMs=500`
  before `soft_stop_started`.

### 4) BLE Transport and FTMS Control Layer
- `app/src/main/java/io/github/ewoc2026/ewoc/ble/FtmsBleClient.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/ble/FtmsController.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/ble/HrBleClient.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/ble/HrReconnectCoordinator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/ble/BleDeviceScanner.kt`

Responsibilities:
- Manage BLE link setup/teardown and service discovery.
- Serialize FTMS Control Point commands (single in-flight command model).
- Apply timeout handling and stale-callback protection.
- Provide bounded HR reconnect behavior.

### 5) Workout Import and Execution Layer
- `app/src/main/java/io/github/ewoc2026/ewoc/workout/WorkoutImportService.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/workout/ZwoParser.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/workout/ExecutionWorkoutMapper.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/workout/runner/WorkoutStepper.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/workout/runner/WorkoutRunner.kt`

Responsibilities:
- Parse imported workout files.
- Convert parsed workouts into executable segments.
- Preserve canonical `.ewo` metadata through the shared parser/editor/import stack,
  including v1.6 root `title_localized` / `description_localized` companions plus earlier
  document `uid`/`revision`, segment `label`/`note`, `free_ride`, and structured message
  timing alongside legacy `when = "start"` compatibility. The bundled workout catalog under
  `app/src/main/assets/workouts/` is still intentionally authored as canonical `.ewo` `1.5`
  even though the import path accepts and preserves `1.6`.
- Treat malformed root `tags` metadata as intake-only noise instead of a hard
  blocker for otherwise valid canonical workouts: import/open paths may drop
  invalid or surplus root tags, but canonical parsing/export stays strict so
  the file contract itself does not silently widen.
- Keep root-tag authoring on that same canonical contract instead of allowing
  desktop-only free text: desktop tag editing now validates against the shared
  `EwoEngine` tag-policy helper and rejects invalid or surplus tags before
  they enter editor document state.
- Keep built-in workouts on the same canonical import seam by loading bundled
  `.ewo` assets from `app/src/main/assets/workouts/` instead of maintaining a
  separate hardcoded workout model.
- Drive timed target updates for FTMS power control.
- Convert canonically specified imported heart-rate-targeted `ergo_workout`
  steady steps into the shared `ExecutionWorkout` model early enough for
  preview metrics and charts, while deferring the hard capability preflight to
  real session start when trainer control can actually exist.
- Keep imported-HR runtime transition rules behind one small runner-owned
  controller seam (`ImportedHrRuntimeControllerV1`) so future session wiring
  can consume live telemetry snapshots without pushing mutable state into the
  pure `ImportedHrRuntimeStateMachineV1`.
- Keep imported heart-rate-targeted `ergo_workout` timelines on a bounded
  session-owned supervision path that currently supports start preflight,
  signal-loss fallback/stop, and stop-on-control-loss while still avoiding a
  broader adaptive HR control loop. The current intended implementation scope
  for the first executable slice is captured in `docs/hr-workouts-v1-plan.md`
  and the safety constraints remain documented in `docs/HR_CONTROL_RULES.md`.
- Feed that same imported-HR supervision path from both real FTMS telemetry and
  debug mock-trainer telemetry so fast validation stays representative of the
  bounded runtime behavior.
- Keep the mock-trainer start path responsible for reconnecting the selected
  external HR strap too; imported-HR preflight depends on the same live HR
  signal there even though FTMS transport is synthetic.
- Re-evaluate that imported-HR supervision path immediately when the external
  HR BLE channel changes, because Polar-style disconnects can happen while
  cadence stays positive and the trainer link keeps streaming unchanged FTMS
  packets.
- Emit imported-HR diagnostics that distinguish the resolved live HR source
  (`ftms_indoor_bike`, `external_heart_rate`, or `none`) from the raw FTMS and
  external readings, so real-device validation can prove whether fallback was
  suppressed by source precedence instead of missing trigger wiring.
- Bind imported-HR controller ownership to the active execution segment instead
  of the whole imported workout, so power ramps and other non-HR steps stay on
  the deterministic power-led runner path even when the same imported workout
  also contains HR steady segments later.
- For imported-HR workouts, treat the external HR channel as the primary live
  source whenever it is available; trainer-integrated FTMS HR is only the
  fallback source when no external HR reading is present.
- When that probe sees `HR=0`, also emit the bounded signal-loss transition the
  runtime would intend from the current state, so live debugging can separate
  "loss semantics should fire here" from "the present gate only reacts to
  `null`".
- During active imported-HR telemetry handling, treat resolved `HR=0` the same
  as missing HR for the bounded signal-loss fallback path.
- In the FILE-based menu flow, prefer Android's direct `OpenDocument` picker as
  the default workout-import path; keep linked-folder SAF tree binding as an
  optional acceleration path for repeat imports and export-related flows rather
  than a required first step.
- Resolve imported-HR safety fallback and hard-cap throttle targets relative to
  the segment's `initialPowerWatts`, then clamp them to one code-owned global
  safety max watt ceiling so the emergency path does not inherit rider-unsafe
  absolute watts from workout metadata alone.
- Apply a short startup grace only for trainer-integrated imported-HR starts
  when the resolved source is `ftms_indoor_bike` and HR is still `0`, so
  normal trainer sensor warmup does not look like a real mid-session signal
  loss.
- Imported-HR bounded control now nudges power in conservative `5 W` steps,
  enforces a `15 s` holdoff after each upward correction, and keeps faster
  downward corrections available when HR drifts above the target band.
- If imported-HR telemetry remains below target at `maxPowerWatts`, surface the
  degraded session status `below target at maximum power`; if telemetry remains
  above target at `minPowerWatts`, surface `above target at minimum power`
  while continuing normal hard-cap supervision.
- When the active runner segment is `HEART_RATE_STEADY`, `SessionScreen` must
  present that block as an explicit HR-control mode: the old split top-row
  `HR` + `Power` cards collapse into one full-width `HR control` card that
  shows current HR plus target band as the primary value and power only as a
  supporting adjustment-range hint. Power-led steps still return to the normal
  two-card top row so the ownership change stays legible.

### 6) Session Metrics and Persistence Layer
- `app/src/main/java/io/github/ewoc2026/ewoc/session/SessionManager.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/session/ActualTssAccumulator.kt`
- `app/src/main/java/io/github/ewoc2026/ewoc/session/SessionStorage.kt`

Responsibilities:
- Aggregate power/cadence/HR/session duration data.
- Compute summary metrics including Actual TSS.
- Preserve logical cumulative `distance` / `energy` totals even when a trainer
  restarts those counters after a fresh FTMS transport/session start by
  applying explicit app-owned offsets.
- Preserve one logical ride across trainer-session hard cutovers by carrying
  one stopped segment forward into the next fresh telemetry-only start and
  merging the resulting summary/export timeline afterward.
- Persist session summaries.

Current continuation note:
- Same-session FTMS reconnect variants did not restore rider-local manual
  power/resistance control on the real trainer, but a full
  `SUMMARY -> MENU -> fresh telemetry-only start` handoff did. The current
  implementation therefore moves `Continue ride` toward that hard-cutover
  model while preserving one logical summary/export through SessionManager
  continuation carryover plus trainer-metric bridging. The remaining required
  proof is one end-to-end live validation of the user-facing `Continue ride`
  action on device.

## Core Invariants

1. FTMS control writes are serialized.
- `FtmsController` allows only one active control command at a time.

2. Session start does not become active before control is granted.
- `CONNECTING -> SESSION` transition is gated by request-control success flow.

3. Stop flow is explicit.
- `STOPPING` state exists to prevent ambiguous end-session transitions.

4. UI state is callback-driven, not timer-driven business logic.
- BLE callbacks and orchestrator events decide state transitions.

5. Menu availability probes are passive.
- Menu probe scans only advertisements and does not claim GATT/control ownership.

6. HR control stays safety-gated until a separate supervisor exists.
- Future HR-controlled execution must follow `docs/HR_CONTROL_RULES.md`, including an
  independent `SafetySupervisor`, hard HR cap enforcement, and "no increase on
  uncertainty" behavior.
- The planned first executable slice is additionally constrained by
  `docs/hr-workouts-v1-plan.md`: start only with explicit capability preflight,
  conservative bounded steady-HR control, signal-loss fallback, cap-throttle,
  and stop behavior.

## Session Flow (High Level)

### Start Flow
1. User selects workout and trainer on `MENU`.
2. User taps `Start session`.
3. Orchestrator opens FTMS link and enters `CONNECTING`.
4. After FTMS ready + control granted, app enters `SESSION`.
5. Workout runner starts when cadence gating conditions are met.

### Stop Flow
1. User taps end session.
2. App enters `STOPPING` and sends FTMS stop/reset sequence.
3. Summary finalizes on acknowledgement, disconnect, or stop timeout fallback.
4. App navigates to `SUMMARY`.

## Why This Shape Works
- BLE protocol handling stays out of composables.
- UI can be reworked without changing FTMS behavior.
- Workout parsing/mapping/execution can evolve independently from BLE transport.
- Tests can target protocol and flow logic without real hardware.

## Suggested Read Order for New Contributors
1. `MainActivityContent.kt`
2. `MainViewModel.kt`
3. `DocumentsFolderUiState.kt`
4. `DebugAutomationUiState.kt`
5. `ProfileSettingsUiState.kt`
6. `baseline/BaselineFitnessTestProtocol.kt`
7. `baseline/BaselineFitnessTestStateMachine.kt`
8. `baseline/BaselineFitnessTestResultCalculator.kt`
9. `baseline/BaselineFitnessTestSettingsStorage.kt`
10. `baseline/BaselineFitnessTestResultPromotionCoordinator.kt`
11. `SessionRuntimeUiState.kt`
12. `SessionStartEligibilityUiState.kt`
13. `SummaryFitUiState.kt`
14. `WorkoutEditorSessionUiState.kt`
15. `DeviceSelectionFacade.kt`
16. `MenuStatusProbeFacade.kt`
17. `DocumentsFolderImportCoordinator.kt`
18. `CompatibilityCheckLaunchCoordinator.kt`
19. `CompatibilityCheckCoordinator.kt`
20. `CompatibilityCheckRunFacade.kt`
21. `SummaryFitAutoExportCoordinator.kt`
22. `SummaryExitCoordinator.kt`
23. `SummaryFitExportCoordinator.kt`
24. `SummaryFitExportPreferenceCoordinator.kt`
25. `SummaryFitShareCoordinator.kt`
26. `WorkoutEditorHydrationCoordinator.kt`
27. `WorkoutEditorFlowCoordinator.kt`
28. `WorkoutEditorDraftCoordinator.kt`
29. `WorkoutEditorPersistenceCoordinator.kt`
30. `WorkoutEditorSaveCoordinator.kt`
31. `SessionOrchestrator.kt`
32. `FtmsBleClient.kt` and `FtmsController.kt`
33. `WorkoutImportService.kt` and `ExecutionWorkoutMapper.kt`
34. `SessionManager.kt`
