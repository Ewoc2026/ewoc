# FTMS Protocol Reference

## Purpose
This document is a practical FTMS reference for Ewoc maintainers.

It combines:
- Public Bluetooth SIG references (profile/service, assigned numbers, public ICS/TS documents).
- The current Ewoc FTMS implementation contract.

The target is interoperability and maintenance safety, not a full replacement for licensed specification texts.

## Scope and Limits
- FTMS normative bit-level requirements are defined in Bluetooth SIG specifications.
- This document only uses public material and implementation evidence in this repository.
- Where behavior is app-specific, it is explicitly labeled as implementation-specific.

## Terminology and Standards Map
- **FTMP (Fitness Machine Profile)**: profile-level interoperability model.
- **FTMS (Fitness Machine Service)**: GATT service used for machine features, telemetry, and control point procedures.
- **Control Point**: command/response mechanism for trainer control procedures.

In practice for this app:
- FTMS transport is BLE.
- Indoor bike telemetry comes from Indoor Bike Data notifications.
- Control procedures are sent via Fitness Machine Control Point indications.

## Core Assigned Numbers Used by FTMS
Service:
- Fitness Machine Service: `0x1826`

Common FTMS characteristics (public assigned numbers):
- Fitness Machine Feature: `0x2ACC`
- Treadmill Data: `0x2ACD`
- Cross Trainer Data: `0x2ACE`
- Step Climber Data: `0x2ACF`
- Stair Climber Data: `0x2AD0`
- Rower Data: `0x2AD1`
- Indoor Bike Data: `0x2AD2`
- Training Status: `0x2AD3`
- Supported Speed Range: `0x2AD4`
- Supported Inclination Range: `0x2AD5`
- Supported Resistance Level Range: `0x2AD6`
- Supported Heart Rate Range: `0x2AD7`
- Supported Power Range: `0x2AD8`
- Fitness Machine Control Point: `0x2AD9`
- Fitness Machine Status: `0x2ADA`

## Public Conformance Takeaways (ICS / TS)
From public FTMS ICS and TS documents:
- FTMS conformance is versioned (public proforma lists FTMS v1.0 baseline and v1.0.1 additions).
- A conformant implementation must support at least one transport (LE and/or BR/EDR depending on product).
- Feature/range/status support is conditional and capability-driven rather than universally mandatory.
- Control point tests assume indication path is configured before executing control procedures.
- Control point procedure coverage in tests includes request-control, reset, target-setting procedures, start/resume, stop/pause, and error cases.

## Ewoc FTMS Implementation Contract
This section is implementation-specific and derived from current source code.

### Required UUIDs in Current App Flow
Current runtime setup requires:
- Service: `0x1826`
- Indoor Bike Data: `0x2AD2`
- Fitness Machine Control Point: `0x2AD9`

If these are missing, setup is aborted.

### Setup Sequence and GATT Ordering
`FtmsBleClient` uses this setup order:
1. Connect and discover services.
2. Resolve FTMS service and required characteristics.
3. Enable local notification routing for Indoor Bike Data.
4. Enable local indication routing for Control Point.
5. Write Control Point CCCD with indication value.
6. Write Indoor Bike Data CCCD with notification value.
7. Mark transport ready only after descriptor setup completes successfully.

Rationale:
- BLE GATT requires strict serialized operations.
- Control point response reliability depends on indication setup success.

### Control Point Command Model in App
`FtmsController` enforces a single in-flight command model:
- Busy state is entered only when write-start succeeds.
- Busy is released only by a matching response or timeout recovery.
- Target power updates follow a last-wins queue while busy.
- Timeout fallback prevents permanent command lock.

Currently emitted opcodes:
- `0x00`: Request Control
- `0x01`: Reset
- `0x05`: Set Target Power (little-endian watts)
- `0x08 0x01`: Stop workout
- `0x08 0x02`: Pause

### Normative FTMS Takeaways For End-Of-Session Semantics
From the Bluetooth SIG Fitness Machine Service 1.0 specification:

- `Request Control (0x00)` grants control permission for supported control
  procedures.
- that control permission remains valid until the BLE connection terminates,
  the Server reports `Control Permission Lost`, or the Client initiates
  `Reset (0x01)`.
- `Reset (0x01)` restores control parameters to their default values and moves
  Training Status to `Idle`.
- `Set Target Power (0x05)` is a normal target-setting procedure; on success,
  the Server shall set target power to the provided parameter.
- `Stop or Pause (0x08)` is its own procedure with a control-information
  parameter of `0x01 = Stop` and `0x02 = Pause`.
- the public procedure table does not define a separate client-initiated
  "clear target power" or "release ERG target without stop/reset" opcode.

Implementation consequence:

- `setTargetPower(0)` may still be a useful trainer-specific probe, but it is
  an app/device convention, not a distinct normative FTMS clear/release
  procedure.
- new debug seams, labels, and investigation plans should therefore be named
  after the actual FTMS procedures (`Set Target Power`, `Stop`, `Pause`,
  `Reset`) unless the distinction is explicitly documented as trainer-specific.

Current response handling:
- Response frame type expected: response-code packet (`0x80` as first byte in current parser logic).
- Success is currently interpreted when result code equals `0x01`.

### Operational Invariants
- No parallel control point writes are allowed.
- Reconnect and stale-callback paths must not resurrect obsolete command state.
- Commands sent before transport-ready are rejected by design.

## Interoperability Checklist for New Trainers
Use this checklist before declaring compatibility:
1. FTMS service (`0x1826`) is present.
2. Indoor Bike Data (`0x2AD2`) and Control Point (`0x2AD9`) are present.
3. CCCD write for control point indications succeeds.
4. Request Control (`0x00`) returns success.
5. Set Target Power (`0x05`) returns deterministic response under load.
6. Stop/Pause procedures are acknowledged and do not leave BUSY state stuck.
7. Reconnect after trainer sleep returns to ready state without stale callbacks affecting state.

## Gaps and Planned Improvements
- Capability gating could be strengthened by actively reading and caching `Fitness Machine Feature` (`0x2ACC`).
- Optional `Fitness Machine Status` (`0x2ADA`) could improve UX state messaging.
- Additional control procedures should be enabled only when feature/range support is confirmed.

## Source References
Retrieved on 2026-02-19.

Bluetooth SIG public references:
- Fitness Machine Profile page:
  - https://www.bluetooth.com/specifications/specs/fitness-machine-profile-1-0/
- Fitness Machine Service page:
  - https://www.bluetooth.com/specifications/specs/fitness-machine-service-1-0/
- Public FTMS ICS proforma (FTMS.ICS.p5):
  - https://files.bluetooth.com/wp-content/uploads/dlm_uploads/2024/10/FTMS.ICS.p5.pdf
- Public FTMS Test Suite (FTMS.TS_.p6):
  - https://files.bluetooth.com/wp-content/uploads/dlm_uploads/2024/10/FTMS.TS_.p6.pdf
- Bluetooth Assigned Numbers:
  - https://www.bluetooth.com/wp-content/uploads/Files/Specification/Assigned_Numbers.pdf?v=1704165718337

Implementation references in this repository:
- `app/src/main/java/com/example/ergometerapp/ble/FtmsBleClient.kt`
- `app/src/main/java/com/example/ergometerapp/ble/FtmsController.kt`
