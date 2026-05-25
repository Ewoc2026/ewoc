package com.example.ergometerapp.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionFreeRideControlCoordinatorTest {

    @Test
    fun enteringFreeRideWithControlSendsOnlyReset() {
        val recorder = ActionRecorder()
        val coordinator = recorder.createCoordinator()

        coordinator.onRunnerTargetWrite(
            targetWatts = null,
            ftmsReady = true,
            controlGranted = true,
        )

        assertEquals(listOf("resetTrainer"), recorder.actions)
        assertFalse(coordinator.shouldRequestSessionControlOnReady())
    }

    @Test
    fun firstStructuredTargetAfterFreeRideRequestsControlThenAppliesTargetOnceGranted() {
        val recorder = ActionRecorder()
        val coordinator = recorder.createCoordinator()

        coordinator.onRunnerTargetWrite(
            targetWatts = null,
            ftmsReady = true,
            controlGranted = true,
        )
        coordinator.onControlOwnershipChanged(
            controlGranted = false,
            ftmsReady = true,
        )
        coordinator.onRunnerTargetWrite(
            targetWatts = 220,
            ftmsReady = true,
            controlGranted = false,
        )

        assertEquals(
            listOf("resetTrainer", "requestControl"),
            recorder.actions,
        )
        assertTrue(coordinator.shouldRequestSessionControlOnReady())

        coordinator.onControlOwnershipChanged(
            controlGranted = true,
            ftmsReady = true,
        )
        coordinator.onRequestControlSucceeded(
            ftmsReady = true,
            controlGranted = true,
        )

        assertEquals(
            listOf(
                "resetTrainer",
                "requestControl",
                "setTargetPower(220)",
            ),
            recorder.actions,
        )
    }

    @Test
    fun pureFreeRideWithoutControlDoesNotReleaseAgainButReacquiresOnNextStructuredTarget() {
        val recorder = ActionRecorder()
        val coordinator = recorder.createCoordinator()

        coordinator.onRunnerTargetWrite(
            targetWatts = null,
            ftmsReady = true,
            controlGranted = false,
        )

        assertTrue(recorder.actions.isEmpty())
        assertFalse(coordinator.shouldRequestSessionControlOnReady())

        coordinator.onRunnerTargetWrite(
            targetWatts = 180,
            ftmsReady = true,
            controlGranted = false,
        )

        assertEquals(listOf("requestControl"), recorder.actions)
        assertTrue(coordinator.shouldRequestSessionControlOnReady())
    }

    @Test
    fun stopTriggeredNullTargetCanBeIgnoredOnceWithoutEnteringFreeRide() {
        val recorder = ActionRecorder()
        val coordinator = recorder.createCoordinator()

        coordinator.ignoreNextNullTargetWriteOnce()
        coordinator.onRunnerTargetWrite(
            targetWatts = null,
            ftmsReady = true,
            controlGranted = true,
        )

        assertTrue(recorder.actions.isEmpty())

        coordinator.onRunnerTargetWrite(
            targetWatts = null,
            ftmsReady = true,
            controlGranted = true,
        )

        assertEquals(listOf("resetTrainer"), recorder.actions)
    }

    @Test
    fun resetTimeoutDoesNotDuplicateReacquireRequestsWhileStructuredTargetIsPending() {
        val recorder = ActionRecorder()
        val coordinator = recorder.createCoordinator()

        coordinator.onRunnerTargetWrite(
            targetWatts = null,
            ftmsReady = true,
            controlGranted = true,
        )
        coordinator.onRunnerTargetWrite(
            targetWatts = 250,
            ftmsReady = true,
            controlGranted = false,
        )
        coordinator.onFreeRideResetTimeout(
            ftmsReady = true,
            controlGranted = false,
        )
        coordinator.onFreeRideResetTimeout(
            ftmsReady = true,
            controlGranted = false,
        )

        assertEquals(
            listOf("resetTrainer", "requestControl"),
            recorder.actions,
        )
    }

    @Test
    fun resetTimeoutWithControlStillGrantedAppliesPendingTargetImmediately() {
        val recorder = ActionRecorder()
        val coordinator = recorder.createCoordinator()

        coordinator.onRunnerTargetWrite(
            targetWatts = null,
            ftmsReady = true,
            controlGranted = true,
        )
        coordinator.onRunnerTargetWrite(
            targetWatts = 205,
            ftmsReady = true,
            controlGranted = true,
        )
        coordinator.onFreeRideResetTimeout(
            ftmsReady = true,
            controlGranted = true,
        )

        assertEquals(
            listOf("resetTrainer", "setTargetPower(205)"),
            recorder.actions,
        )
        assertTrue(coordinator.shouldRequestSessionControlOnReady())
    }

    @Test
    fun resetSucceededWithControlStillGrantedAppliesNextStructuredTargetImmediately() {
        val recorder = ActionRecorder()
        val coordinator = recorder.createCoordinator()

        // Enter FreeRide while control is held — triggers RESET sequence.
        coordinator.onRunnerTargetWrite(
            targetWatts = null,
            ftmsReady = true,
            controlGranted = true,
        )
        assertEquals(listOf("resetTrainer"), recorder.actions)

        // Trainer ACKs the RESET; BLE ownership notification has not fired yet (still granted).
        coordinator.onFreeRideResetSucceeded(
            ftmsReady = true,
            controlGranted = true,
        )
        // No additional side effects until a structured target arrives.
        assertEquals(listOf("resetTrainer"), recorder.actions)

        // First structured target after FreeRide — control is still held, so power is applied
        // directly without a requestControl round-trip.
        coordinator.onRunnerTargetWrite(
            targetWatts = 200,
            ftmsReady = true,
            controlGranted = true,
        )

        assertEquals(
            listOf("resetTrainer", "setTargetPower(200)"),
            recorder.actions,
        )
        // Coordinator has returned to normal session state — will auto-request control on ready.
        assertTrue(coordinator.shouldRequestSessionControlOnReady())
    }

    @Test
    fun regainingControlDuringPureFreeRideDoesNotAutoRequestUntilStructuredTargetReturns() {
        val recorder = ActionRecorder()
        val coordinator = recorder.createCoordinator()

        coordinator.onRunnerTargetWrite(
            targetWatts = null,
            ftmsReady = true,
            controlGranted = false,
        )
        coordinator.onControlOwnershipChanged(
            controlGranted = true,
            ftmsReady = true,
        )

        assertTrue(recorder.actions.isEmpty())
        assertFalse(coordinator.shouldRequestSessionControlOnReady())

        coordinator.onRunnerTargetWrite(
            targetWatts = 190,
            ftmsReady = true,
            controlGranted = true,
        )

        assertEquals(listOf("setTargetPower(190)"), recorder.actions)
        assertTrue(coordinator.shouldRequestSessionControlOnReady())
    }

    @Test
    fun clearResetsAllStateForNextSession() {
        val recorder = ActionRecorder()
        val coordinator = recorder.createCoordinator()

        // Put coordinator into FreeRide state with release in-flight.
        coordinator.onRunnerTargetWrite(targetWatts = null, ftmsReady = true, controlGranted = true)
        assertEquals(listOf("resetTrainer"), recorder.actions)

        // Session teardown before RESET completes.
        coordinator.clear()
        recorder.actions.clear()

        // After clear, coordinator behaves as a fresh instance — no FreeRide state, no pending
        // target, structured power is applied directly when control is held.
        assertTrue(coordinator.shouldRequestSessionControlOnReady())
        coordinator.onRunnerTargetWrite(targetWatts = 180, ftmsReady = true, controlGranted = true)
        assertEquals(listOf("setTargetPower(180)"), recorder.actions)
    }

    @Test
    fun resetSucceededWithControlLostQueuesReacquireBeforeApplyingTarget() {
        val recorder = ActionRecorder()
        val coordinator = recorder.createCoordinator()

        // Enter FreeRide with control — triggers RESET sequence.
        coordinator.onRunnerTargetWrite(targetWatts = null, ftmsReady = true, controlGranted = true)
        assertEquals(listOf("resetTrainer"), recorder.actions)

        // Structured target arrives while RESET is still in-flight; control already lost on BLE.
        coordinator.onRunnerTargetWrite(targetWatts = 210, ftmsReady = true, controlGranted = false)
        // requestControl should NOT fire yet — controlReleaseInFlight is still true.
        assertEquals(listOf("resetTrainer"), recorder.actions)

        // RESET ACK arrives; BLE reports control already gone.
        coordinator.onFreeRideResetSucceeded(ftmsReady = true, controlGranted = false)
        // Now controlReleaseInFlight=false, controlReleased=true → reacquire fires.
        assertEquals(listOf("resetTrainer", "requestControl"), recorder.actions)

        // Control comes back.
        coordinator.onRequestControlSucceeded(ftmsReady = true, controlGranted = true)
        assertEquals(
            listOf("resetTrainer", "requestControl", "setTargetPower(210)"),
            recorder.actions,
        )
    }

    @Test
    fun freeRideReentryWhileReleaseInFlightIsIdempotent() {
        val recorder = ActionRecorder()
        val coordinator = recorder.createCoordinator()

        // First FreeRide entry — control held, triggers RESET.
        coordinator.onRunnerTargetWrite(targetWatts = null, ftmsReady = true, controlGranted = true)
        assertEquals(listOf("resetTrainer"), recorder.actions)

        // Second FreeRide entry before RESET completes (e.g. runner step repeats null target).
        coordinator.onRunnerTargetWrite(targetWatts = null, ftmsReady = true, controlGranted = true)

        // Must not emit a second resetTrainer.
        assertEquals(listOf("resetTrainer"), recorder.actions)
    }

    private class ActionRecorder {
        val actions = mutableListOf<String>()

        fun createCoordinator(): SessionFreeRideControlCoordinator {
            return SessionFreeRideControlCoordinator(
                requestControl = { actions += "requestControl" },
                resetTrainer = { actions += "resetTrainer" },
                setTargetPower = { targetWatts -> actions += "setTargetPower($targetWatts)" },
            )
        }
    }
}
