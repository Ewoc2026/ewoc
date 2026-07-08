package io.github.ewoc2026.ewoc.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import io.github.ewoc2026.ewoc.AI_MENU_CONNECTIVITY_CHECK_TRAINER_ACTION
import io.github.ewoc2026.ewoc.DocumentsFolderWorkoutOption
import io.github.ewoc2026.ewoc.R
import io.github.ewoc2026.ewoc.DeviceSelectionKind
import io.github.ewoc2026.ewoc.HrProfileSex
import io.github.ewoc2026.ewoc.ScannedBleDevice
import io.github.ewoc2026.ewoc.SessionDebugProbeSignal
import io.github.ewoc2026.ewoc.SessionSetupMode
import io.github.ewoc2026.ewoc.ftms.IndoorBikeData
import io.github.ewoc2026.ewoc.logging.AppLog
import io.github.ewoc2026.ewoc.session.SessionPhase
import io.github.ewoc2026.ewoc.session.SessionSummary
import io.github.ewoc2026.ewoc.ui.components.SegmentKind
import io.github.ewoc2026.ewoc.ui.components.WorkoutProfileChart
import io.github.ewoc2026.ewoc.ui.components.WorkoutProfileSegment
import io.github.ewoc2026.ewoc.ui.components.buildWorkoutProfileSegments
import io.github.ewoc2026.ewoc.ui.components.disabledVisibleButtonColors
import io.github.ewoc2026.ewoc.ui.theme.UiSemanticColor
import io.github.ewoc2026.ewoc.ui.theme.UiSpacing
import io.github.ewoc2026.ewoc.workout.DefaultWorkoutTextEventDurationSec
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkout
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkoutStep
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkoutTextEventMapper
import io.github.ewoc2026.ewoc.workout.WorkoutFile
import io.github.ewoc2026.ewoc.workout.resolveActiveWorkoutTextEvent
import io.github.ewoc2026.ewoc.workout.runner.IntervalPartPhase
import io.github.ewoc2026.ewoc.workout.runner.RunnerState
import io.github.ewoc2026.ewoc.workout.runner.RunnerSegmentType
import kotlin.math.roundToInt
import java.util.Locale

private enum class SessionDataLayoutMode {
    FITNESS,
    GOAL_ORIENTED,
}

private val SessionTopRailCompactHeight = 64.dp

private data class SessionTopMetricPresentation(
    val label: String,
    val value: String,
    val emphasized: Boolean,
    val stateDescription: String? = null,
    val supportingText: String? = null,
)

/**
 * Live session UI.
 *
 * This screen surfaces FTMS/HR telemetry and exposes control actions. Buttons
 * are intentionally visible but disabled when control has not been granted, to
 * make the protocol state explicit to the user.
 */
@Composable
internal fun SessionScreen(
    phase: SessionPhase,
    bikeData: IndoorBikeData?,
    heartRate: Int?,
    logicalDistanceMeters: Int?,
    logicalTotalEnergyKcal: Int?,
    ftmsReady: Boolean,
    ftmsControlGranted: Boolean,
    postWorkoutFreerideModeActive: Boolean,
    selectedSessionSetupMode: SessionSetupMode,
    selectedWorkout: WorkoutFile?,
    selectedImportedWorkout: ImportedErgoWorkout? = null,
    selectedWorkoutFileName: String?,
    ftpWatts: Int,
    runnerState: RunnerState,
    sessionDurationSeconds: Int = 0,
    hrProfileAge: Int?,
    hrProfileSex: HrProfileSex?,
    lastTargetPower: Int?,
    workoutExecutionModeMessage: String?,
    workoutExecutionModeIsError: Boolean,
    aiAssistantMessage: String?,
    aiAssistantIsError: Boolean,
    mockTrainerModeEnabled: Boolean,
    timelineSamples: List<io.github.ewoc2026.ewoc.session.SessionSample> = emptyList(),
    isProEntitled: Boolean = false,
    postWorkoutContinuationHandoffVisible: Boolean = false,
    sessionDebugProbeVisible: Boolean = false,
    sessionDebugProbeTitle: String? = null,
    sessionDebugProbeMessage: String? = null,
    sessionDebugProbeDiagnostics: String? = null,
    sessionDebugProbeReceipt: String? = null,
    onEndSession: () -> Unit,
    onWorkoutCompletePresented: () -> Unit,
    onContinueRideAfterWorkoutComplete: () -> Unit,
    onEndSessionAfterWorkoutComplete: () -> Unit,
    onSessionDebugProbeSignal: (SessionDebugProbeSignal) -> Unit = {},
) {
    LaunchedEffect(bikeData) {
        AppLog.telemetryDebug("UI") {
            "SessionScreen bikeData update received hasData=${bikeData != null}"
        }
    }

    val unknown = stringResource(R.string.value_unknown)
    val effectiveHr = heartRate ?: bikeData?.heartRateBpm
    val heartRateValue = stringResource(
        R.string.session_hr_value,
        format0(effectiveHr, unknown)
    )
    val distanceValue = stringResource(
        R.string.summary_distance_value,
        format0(logicalDistanceMeters ?: bikeData?.totalDistanceMeters, unknown)
    )
    val kcalValue = stringResource(
        R.string.session_kcal_value,
        format0(logicalTotalEnergyKcal ?: bikeData?.totalEnergyKcal, unknown)
    )
    val speedValue = stringResource(
        R.string.session_speed_value,
        format1(bikeData?.instantaneousSpeedKmh, unknown),
    )
    val cadenceRpm = bikeData?.instantaneousCadenceRpm
    val roundedCadenceRpm = cadenceRpm?.roundToInt()
    val instantPowerValue = stringResource(
        R.string.session_power_value,
        format0(bikeData?.instantaneousPowerW, unknown),
    )
    val instantCadenceFormatted = format0(roundedCadenceRpm, unknown)
    val targetCadenceRpm = runnerState.targetCadence?.takeIf { it > 0 }
    val instantCadenceValue = if (targetCadenceRpm != null) {
        stringResource(
            R.string.session_cadence_target_value,
            instantCadenceFormatted,
            format0(targetCadenceRpm, unknown),
        )
    } else {
        stringResource(
            R.string.session_cadence_value,
            instantCadenceFormatted,
        )
    }
    val targetPowerWatts = sessionDisplayedTargetPowerWatts(
        runnerState = runnerState,
        fallbackTargetPower = lastTargetPower,
    )
    val effectiveSelectedWorkout = if (selectedSessionSetupMode == SessionSetupMode.TELEMETRY_ONLY) {
        null
    } else {
        selectedWorkout
    }
    val effectiveSelectedImportedWorkout = if (selectedSessionSetupMode == SessionSetupMode.TELEMETRY_ONLY) {
        null
    } else {
        selectedImportedWorkout
    }
    val workoutSegments = remember(
        effectiveSelectedWorkout,
        effectiveSelectedImportedWorkout,
        ftpWatts,
    ) {
        when {
            effectiveSelectedWorkout != null -> buildWorkoutProfileSegments(effectiveSelectedWorkout)
            effectiveSelectedImportedWorkout != null ->
                buildWorkoutProfileSegments(effectiveSelectedImportedWorkout, ftpWatts)
            else -> emptyList()
        }
    }
    val isTelemetryOnly = selectedSessionSetupMode == SessionSetupMode.TELEMETRY_ONLY
    val totalWorkoutSec = if (isTelemetryOnly) {
        null
    } else {
        workoutSegments.sumOf { it.durationSec }
            .takeIf { it > 0 }
            ?: effectiveSelectedImportedWorkout?.totalDurationSec?.takeIf { it > 0 }
    }
    val elapsedWorkoutSec = if (isTelemetryOnly) {
        sessionDurationSeconds
    } else {
        runnerState.workoutElapsedSec?.coerceAtLeast(0) ?: 0
    }
    val remainingWorkoutSec = totalWorkoutSec?.let { total ->
        (total - elapsedWorkoutSec).coerceAtLeast(0)
    }
    val elapsedText = formatTime(elapsedWorkoutSec, unknown)
    val remainingText = formatTime(remainingWorkoutSec, unknown)
    val totalText = formatTime(totalWorkoutSec, unknown)
    val elapsedOfTotalText = stringResource(
        R.string.session_elapsed_of_total_value,
        elapsedText,
        totalText,
    )
    val structuredWorkoutComplete = totalWorkoutSec != null &&
        totalWorkoutSec > 0 &&
        remainingWorkoutSec == 0
    val workoutComplete = structuredWorkoutComplete && !postWorkoutFreerideModeActive
    val activeSegment = remember(workoutSegments, runnerState.workoutElapsedSec, workoutComplete) {
        currentWorkoutProfileSegment(
            workoutSegments = workoutSegments,
            elapsedSec = runnerState.workoutElapsedSec,
            completed = workoutComplete,
        )
    }
    val workoutName = resolveWorkoutDisplayName(
        selectedWorkout = effectiveSelectedWorkout,
        selectedImportedWorkout = effectiveSelectedImportedWorkout,
        selectedSessionSetupMode = selectedSessionSetupMode,
        selectedWorkoutFileName = selectedWorkoutFileName,
        fallback = unknown,
        telemetryOnlyLabel = stringResource(R.string.menu_setup_mode_telemetry_only_title),
    )
    val workoutDescription = effectiveSelectedWorkout?.description
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: effectiveSelectedImportedWorkout?.description
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    val workoutDescriptionForDialog = workoutDescription ?: unknown
    val showWorkoutInfoDialog = rememberSaveable { mutableStateOf(false) }
    val workoutCompletionAcknowledged = rememberSaveable(
        selectedWorkoutFileName,
        totalWorkoutSec,
    ) {
        mutableStateOf(false)
    }
    val showWorkoutCompleteDialog = rememberSaveable(
        selectedWorkoutFileName,
        totalWorkoutSec,
    ) {
        mutableStateOf(false)
    }
    LaunchedEffect(structuredWorkoutComplete) {
        if (structuredWorkoutComplete && !workoutCompletionAcknowledged.value) {
            showWorkoutCompleteDialog.value = true
            workoutCompletionAcknowledged.value = true
            onWorkoutCompletePresented()
        }
    }
    val hrZoneValue = sessionHeartRateZoneLabel(
        currentHeartRate = effectiveHr,
        profileAge = hrProfileAge,
        profileSex = hrProfileSex,
        unknown = unknown,
        missingProfile = stringResource(R.string.session_hr_zone_set_profile),
    )
    val hrAndZoneValue = stringResource(
        R.string.session_hr_zone_combined_value,
        heartRateValue,
        hrZoneValue,
    )
    val activeImportedHrStep = remember(
        effectiveSelectedImportedWorkout,
        runnerState.segmentType,
        runnerState.sourceStepIndex,
    ) {
        resolveActiveImportedHrStep(
            workout = effectiveSelectedImportedWorkout,
            runnerState = runnerState,
        )
    }
    val stepTypeValue = sessionStepTypeLabel(
        phase = phase,
        runnerState = runnerState,
        cadenceRpm = cadenceRpm,
        activeSegment = activeSegment,
        workoutSegments = workoutSegments,
        isTelemetryOnly = isTelemetryOnly,
        unknown = unknown,
    )
    val stepRemainingValue = formatTime(runnerState.stepRemainingSec, unknown)
    val targetPowerValue = sessionTargetPowerLabel(
        runnerState = runnerState,
        activeSegment = activeSegment,
        ftpWatts = ftpWatts,
        fallbackTargetPower = lastTargetPower,
        unknown = unknown,
    )
    val instantAndTargetPowerValue = stringResource(
        R.string.session_instant_target_power_value,
        instantPowerValue,
        targetPowerValue,
    )
    val primaryTopMetric = activeImportedHrStep?.let { step ->
        SessionTopMetricPresentation(
            label = stringResource(R.string.session_hr_control_label),
            value = stringResource(
                R.string.session_hr_control_target_band_value,
                heartRateValue,
                step.lowBpm,
                step.highBpm,
            ),
            emphasized = true,
            stateDescription = stringResource(
                R.string.session_hr_control_metric_state_description,
                heartRateValue,
                step.lowBpm,
                step.highBpm,
                step.minPowerWatts,
                step.maxPowerWatts,
            ),
            supportingText = stringResource(
                R.string.session_hr_control_power_range_value,
                step.minPowerWatts,
                step.maxPowerWatts,
            ),
        )
    } ?: SessionTopMetricPresentation(
        label = stringResource(R.string.session_hr_zone_combined_label),
        value = hrAndZoneValue,
        emphasized = true,
    )
    val secondaryTopMetric = if (activeImportedHrStep == null) {
        SessionTopMetricPresentation(
            label = stringResource(R.string.session_instant_target_power_label),
            value = instantAndTargetPowerValue,
            emphasized = true,
        )
    } else {
        null
    }
    val sessionDataLayout = SessionDataLayoutMode.GOAL_ORIENTED
    val sessionIssues = buildList {
        if (!ftmsReady) {
            add(stringResource(R.string.session_issue_ftms_not_ready))
        }
        if (ftmsReady && !ftmsControlGranted && !postWorkoutFreerideModeActive) {
            add(stringResource(R.string.session_issue_control_missing))
        }
    }
    val configuration = LocalConfiguration.current
    val preferredLanguageTags = remember(configuration) {
        val locales = ConfigurationCompat.getLocales(configuration)
        buildList {
            for (index in 0 until locales.size()) {
                locales[index]?.toLanguageTag()?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
    val sessionWorkoutTextEvents = remember(
        effectiveSelectedWorkout,
        effectiveSelectedImportedWorkout,
        preferredLanguageTags,
    ) {
        when {
            effectiveSelectedWorkout != null -> effectiveSelectedWorkout.textEvents
            effectiveSelectedImportedWorkout != null -> ImportedErgoWorkoutTextEventMapper.map(
                workout = effectiveSelectedImportedWorkout,
                preferredLanguageTags = preferredLanguageTags,
            )
            else -> emptyList()
        }
    }
    val activeTextEvent = resolveActiveWorkoutTextEvent(
        textEvents = sessionWorkoutTextEvents,
        workoutElapsedSec = runnerState.workoutElapsedSec,
        defaultDurationSec = DefaultWorkoutTextEventDurationSec,
    )
    val runnerIsPaused = phase == SessionPhase.RUNNING &&
        runnerState.running &&
        runnerState.paused
    val sessionTextEventMessage = activeTextEvent
        ?.message
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { message ->
            if (runnerIsPaused) {
                val pausedLabel = stringResource(R.string.session_workout_state_paused)
                "$pausedLabel · $message"
            } else {
                message
            }
        }
    val executionModeMessage = workoutExecutionModeMessage
    val executionModeIsError = workoutExecutionModeIsError
    val waitingForStart = isWaitingStartState(
        phase = phase,
        runnerState = runnerState,
        cadenceRpm = cadenceRpm,
    )
    val endSessionCtaEmphasized = phase == SessionPhase.RUNNING && !waitingForStart
    val endSessionEnabled = phase == SessionPhase.RUNNING
    val showQuitToSummaryButton = phase == SessionPhase.RUNNING
    val showConnectivityStrip = sessionIssues.isNotEmpty() || executionModeIsError
    val sessionState = sessionStateLabel(
        phase = phase,
        runnerState = runnerState,
        cadenceRpm = cadenceRpm,
        isTelemetryOnly = isTelemetryOnly,
        postWorkoutFreerideModeActive = postWorkoutFreerideModeActive,
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val layoutMode = resolveAdaptiveLayoutMode(width = maxWidth, height = maxHeight)
        val isLandscapeBaseline = maxWidth > maxHeight
        val showTwoPaneBaseline = layoutMode.isTwoPane() && isLandscapeBaseline
        val phoneLandscapeDenseLayout =
            layoutMode == AdaptiveLayoutMode.SINGLE_PANE_DENSE && isLandscapeBaseline
        val paneWeights = layoutMode.paneWeights()
        val widthClass = resolveAdaptiveWidthClass(maxWidth)
        val phonePortraitLayout = widthClass == AdaptiveWidthClass.COMPACT && !isLandscapeBaseline
        val compactTopMetrics = maxWidth < SessionTopMetricsCompactWidth || phoneLandscapeDenseLayout
        val compactSessionChrome = showTwoPaneBaseline || phoneLandscapeDenseLayout
        val sectionVerticalSpacing = if (compactSessionChrome) UiSpacing.md else UiSpacing.lg
        val sectionVerticalPadding = if (compactSessionChrome) UiSpacing.md else UiSpacing.lg
        val topRailPadding = if (compactSessionChrome) UiSpacing.sm else UiSpacing.lg
        val sectionHorizontalPadding = if (
            widthClass == AdaptiveWidthClass.COMPACT || phoneLandscapeDenseLayout
        ) {
            UiSpacing.md
        } else {
            UiSpacing.xl
        }
        val topRailModifier = Modifier
            .padding(horizontal = sectionHorizontalPadding)
            .padding(top = topRailPadding)

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .fillMaxWidth()
                    .widthIn(max = SessionMaxContentWidth)
                    .testTag("sessionScreenRoot"),
            ) {
                if (phoneLandscapeDenseLayout) {
                    SessionDenseTopRail(
                        sessionState = sessionState,
                        workoutTitle = workoutName,
                        workoutDescription = workoutDescription,
                        onOpenWorkoutInfo = { showWorkoutInfoDialog.value = true },
                        onEndSession = onEndSession,
                        endSessionEnabled = endSessionEnabled,
                        endSessionCtaEmphasized = endSessionCtaEmphasized && showQuitToSummaryButton,
                        modifier = topRailModifier,
                    )
                } else {
                    SessionStateRail(
                        sessionState = sessionState,
                        workoutTitle = workoutName,
                        workoutDescription = workoutDescription,
                        mockTrainerModeEnabled = mockTrainerModeEnabled,
                        compact = compactSessionChrome,
                        portraitBaseline = !isLandscapeBaseline,
                        landscapeTwoPaneBaseline = showTwoPaneBaseline,
                        onOpenWorkoutInfo = { showWorkoutInfoDialog.value = true },
                        onEndSession = onEndSession,
                        endSessionEnabled = endSessionEnabled,
                        endSessionCtaEmphasized = endSessionCtaEmphasized && showQuitToSummaryButton,
                        modifier = topRailModifier,
                    )
                }

                if (showTwoPaneBaseline) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = sectionHorizontalPadding)
                            .padding(vertical = sectionVerticalPadding)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(sectionVerticalSpacing),
                    ) {
                        // Keep the tablet-landscape baseline on a fixed viewport layout so the
                        // chart measures against the true remaining height instead of a LazyColumn
                        // item that also has scroll padding applied around it.
                        Column(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(sectionVerticalSpacing),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(sectionVerticalSpacing),
                                verticalAlignment = Alignment.Top,
                            ) {
                                SessionDataLayoutSection(
                                    selectedLayout = sessionDataLayout,
                                    workoutRemainingValue = remainingText,
                                    elapsedOfTotalValue = elapsedOfTotalText,
                                    distanceValue = distanceValue,
                                    energyValue = kcalValue,
                                    speedValue = speedValue,
                                    stepRemainingValue = stepRemainingValue,
                                    stepTypeValue = stepTypeValue,
                                    primaryTopMetric = primaryTopMetric,
                                    secondaryTopMetric = secondaryTopMetric,
                                    compact = compactTopMetrics,
                                    modifier = Modifier.weight(0.6f),
                                )
                                SessionMessagePanel(
                                    statusOverrideMessage = sessionTextEventMessage,
                                    workoutExecutionModeMessage = executionModeMessage,
                                    workoutExecutionModeIsError = executionModeIsError,
                                    aiAssistantMessage = aiAssistantMessage,
                                    aiAssistantIsError = aiAssistantIsError,
                                    workoutComplete = workoutComplete,
                                    compact = compactSessionChrome,
                                    modifier = Modifier.weight(0.4f).fillMaxHeight(),
                                )
                            }
                            BoxWithConstraints(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            ) {
                                // Let the chart consume almost the full card body in tablet
                                // landscape so the plot remains the dominant visual anchor.
                                val chartDockHeight = (maxHeight - 36.dp)
                                    .coerceAtLeast(SessionWorkoutChartHeight)
                                WorkoutProgressSection(
                                    statusOverrideMessage = sessionTextEventMessage,
                                    workoutSegments = workoutSegments,
                                    ftpWatts = ftpWatts,
                                    runnerState = runnerState,
                                    phase = phase,
                                    cadenceRpm = cadenceRpm,
                                    remainingText = remainingText,
                                    elapsedOfTotalText = elapsedOfTotalText,
                                    unknown = unknown,
                                    lastTargetPower = lastTargetPower,
                                    workoutComplete = workoutComplete,
                                    workoutExecutionModeMessage = executionModeMessage,
                                    workoutExecutionModeIsError = executionModeIsError,
                                    aiAssistantMessage = aiAssistantMessage,
                                    aiAssistantIsError = aiAssistantIsError,
                                    showTimingMetrics = false,
                                    showStepMetrics = false,
                                    showStatusContent = false,
                                    heartRateValue = heartRateValue,
                                    hrZoneValue = hrZoneValue,
                                    targetPowerWatts = targetPowerWatts,
                                    instantPowerValue = instantPowerValue,
                                    instantCadenceValue = instantCadenceValue,
                                    kcalValue = kcalValue,
                                    chartHeight = chartDockHeight,
                                    timelineSamples = timelineSamples,
                                    isTelemetryOnly = isTelemetryOnly,
                                    isProEntitled = isProEntitled,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        if (showConnectivityStrip) {
                            SessionConnectivityStrip(
                                ftmsReady = ftmsReady,
                                ftmsControlGranted = ftmsControlGranted,
                                sessionIssues = sessionIssues,
                                workoutExecutionModeMessage = executionModeMessage,
                                workoutExecutionModeIsError = executionModeIsError,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = sectionHorizontalPadding)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = sectionVerticalPadding),
                        verticalArrangement = Arrangement.spacedBy(sectionVerticalSpacing),
                    ) {
                        item(key = "sessionDataLayout") {
                            if (phoneLandscapeDenseLayout) {
                            Row(
                                modifier = Modifier.fillMaxWidth().fillParentMaxHeight(),
                                horizontalArrangement = Arrangement.spacedBy(sectionVerticalSpacing),
                                verticalAlignment = Alignment.Top,
                            ) {
                                // Left pane: metrics + message panel filling remaining height.
                                Column(
                                    modifier = Modifier.weight(0.6f).fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(sectionVerticalSpacing),
                                ) {
                                    SessionDataLayoutSection(
                                        selectedLayout = sessionDataLayout,
                                        workoutRemainingValue = remainingText,
                                        elapsedOfTotalValue = elapsedOfTotalText,
                                        distanceValue = distanceValue,
                                        energyValue = kcalValue,
                                        speedValue = speedValue,
                                        stepRemainingValue = stepRemainingValue,
                                        stepTypeValue = stepTypeValue,
                                        primaryTopMetric = primaryTopMetric,
                                        secondaryTopMetric = secondaryTopMetric,
                                        compact = true,
                                    )
                                    SessionMessagePanel(
                                        statusOverrideMessage = sessionTextEventMessage,
                                        workoutExecutionModeMessage = executionModeMessage,
                                        workoutExecutionModeIsError = executionModeIsError,
                                        aiAssistantMessage = aiAssistantMessage,
                                        aiAssistantIsError = aiAssistantIsError,
                                        workoutComplete = workoutComplete,
                                        compact = true,
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                    )
                                }
                                // Right pane: chart only, fills full pane height.
                                // BoxWithConstraints lets us compute chartHeight to fill the pane.
                                BoxWithConstraints(
                                    modifier = Modifier.weight(1.4f).fillMaxHeight(),
                                ) {
                                    // Connectivity strip (compact SectionCard + title + lines) ≈ 100dp.
                                    val stripReserved = if (showConnectivityStrip) {
                                        100.dp + sectionVerticalSpacing
                                    } else {
                                        0.dp
                                    }
                                    val chartDockHeight = (maxHeight - 32.dp - stripReserved)
                                        .coerceAtLeast(SessionWorkoutChartHeightDense)
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(sectionVerticalSpacing),
                                    ) {
                                        if (showConnectivityStrip) {
                                            SessionConnectivityStrip(
                                                ftmsReady = ftmsReady,
                                                ftmsControlGranted = ftmsControlGranted,
                                                sessionIssues = sessionIssues,
                                                workoutExecutionModeMessage = executionModeMessage,
                                                workoutExecutionModeIsError = executionModeIsError,
                                            )
                                        }
                                        WorkoutProgressSection(
                                            statusOverrideMessage = sessionTextEventMessage,
                                            workoutSegments = workoutSegments,
                                            ftpWatts = ftpWatts,
                                            runnerState = runnerState,
                                            phase = phase,
                                            cadenceRpm = cadenceRpm,
                                            remainingText = remainingText,
                                            elapsedOfTotalText = elapsedOfTotalText,
                                            unknown = unknown,
                                            lastTargetPower = lastTargetPower,
                                            workoutComplete = workoutComplete,
                                            workoutExecutionModeMessage = executionModeMessage,
                                            workoutExecutionModeIsError = executionModeIsError,
                                            aiAssistantMessage = aiAssistantMessage,
                                            aiAssistantIsError = aiAssistantIsError,
                                            showTimingMetrics = false,
                                            showStepMetrics = false,
                                            showStatusContent = false,
                                            heartRateValue = heartRateValue,
                                            hrZoneValue = hrZoneValue,
                                            targetPowerWatts = targetPowerWatts,
                                            instantPowerValue = instantPowerValue,
                                            instantCadenceValue = instantCadenceValue,
                                            kcalValue = kcalValue,
                                            chartHeight = chartDockHeight,
                                            timelineSamples = timelineSamples,
                                            isTelemetryOnly = isTelemetryOnly,
                                            compactChart = true,
                                            isProEntitled = isProEntitled,
                                        )
                                    }
                                }
                            }
                            } else {
                                Column(
                                    modifier = Modifier.fillParentMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(sectionVerticalSpacing),
                                ) {
                                    SessionDataLayoutSection(
                                        selectedLayout = sessionDataLayout,
                                        workoutRemainingValue = remainingText,
                                        elapsedOfTotalValue = elapsedOfTotalText,
                                        distanceValue = distanceValue,
                                        energyValue = kcalValue,
                                        speedValue = speedValue,
                                        stepRemainingValue = stepRemainingValue,
                                        stepTypeValue = stepTypeValue,
                                        primaryTopMetric = primaryTopMetric,
                                        secondaryTopMetric = secondaryTopMetric,
                                        compact = compactTopMetrics,
                                        phonePortraitLayout = phonePortraitLayout,
                                    )

                                    WorkoutProgressSection(
                                        statusOverrideMessage = sessionTextEventMessage,
                                        workoutSegments = workoutSegments,
                                        ftpWatts = ftpWatts,
                                        runnerState = runnerState,
                                        phase = phase,
                                        cadenceRpm = cadenceRpm,
                                        remainingText = remainingText,
                                        elapsedOfTotalText = elapsedOfTotalText,
                                        unknown = unknown,
                                        lastTargetPower = lastTargetPower,
                                        workoutComplete = workoutComplete,
                                        workoutExecutionModeMessage = executionModeMessage,
                                        workoutExecutionModeIsError = executionModeIsError,
                                        aiAssistantMessage = aiAssistantMessage,
                                        aiAssistantIsError = aiAssistantIsError,
                                        showTimingMetrics = false,
                                        showStepMetrics = false,
                                        phonePortraitLayout = phonePortraitLayout,
                                        chartHeight = if (phonePortraitLayout) {
                                            SessionWorkoutChartHeight
                                        } else {
                                            SessionWorkoutChartHeightTabletPortrait
                                        },
                                        heartRateValue = heartRateValue,
                                        hrZoneValue = hrZoneValue,
                                        targetPowerWatts = targetPowerWatts,
                                        instantPowerValue = instantPowerValue,
                                        instantCadenceValue = instantCadenceValue,
                                        kcalValue = kcalValue,
                                        timelineSamples = timelineSamples,
                                        isTelemetryOnly = isTelemetryOnly,
                                        isProEntitled = isProEntitled,
                                        endButtonSlot = if (showQuitToSummaryButton) {
                                            {
                                                SessionEndButton(
                                                    onClick = onEndSession,
                                                    enabled = endSessionEnabled,
                                                    emphasized = endSessionCtaEmphasized,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 48.dp)
                                                        .testTag("sessionQuitButton"),
                                                )
                                            }
                                        } else null,
                                    )
                                    SessionMessagePanel(
                                        statusOverrideMessage = sessionTextEventMessage,
                                        workoutExecutionModeMessage = executionModeMessage,
                                        workoutExecutionModeIsError = executionModeIsError,
                                        aiAssistantMessage = aiAssistantMessage,
                                        aiAssistantIsError = aiAssistantIsError,
                                        workoutComplete = workoutComplete,
                                        compact = compactTopMetrics,
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                    )
                                }
                            }
                        }

                        // Two-pane landscape relies on the header stop button; no rail needed here.

                        if (showConnectivityStrip && !phoneLandscapeDenseLayout) {
                            item(key = "sessionConnectivityStrip") {
                                SessionConnectivityStrip(
                                    ftmsReady = ftmsReady,
                                    ftmsControlGranted = ftmsControlGranted,
                                    sessionIssues = sessionIssues,
                                    workoutExecutionModeMessage = executionModeMessage,
                                    workoutExecutionModeIsError = executionModeIsError,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (sessionDebugProbeVisible) {
        SessionDebugProbeOverlayHost(
            title = sessionDebugProbeTitle,
            message = sessionDebugProbeMessage,
            diagnostics = sessionDebugProbeDiagnostics,
            lastSignalReceipt = sessionDebugProbeReceipt,
            onSignal = onSessionDebugProbeSignal,
        )
    }

    if (postWorkoutContinuationHandoffVisible) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
        ) {
            TransitionStatusScreen(
                statusText = stringResource(R.string.session_continue_ride_handoff_status),
                hintText = stringResource(R.string.session_continue_ride_handoff_hint),
            )
        }
    }

    if (showWorkoutInfoDialog.value) {
        AlertDialog(
            onDismissRequest = { showWorkoutInfoDialog.value = false },
            title = { Text(stringResource(R.string.session_workout_info_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.session_workout_info_name, workoutName),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.session_workout_info_description,
                            workoutDescriptionForDialog,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showWorkoutInfoDialog.value = false }) {
                    Text(stringResource(R.string.menu_dialog_ok))
                }
            },
        )
    }
    if (showWorkoutCompleteDialog.value) {
        AlertDialog(
            onDismissRequest = { showWorkoutCompleteDialog.value = false },
            title = { Text(stringResource(R.string.session_workout_complete)) },
            text = { Text(stringResource(R.string.session_workout_complete_reward_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWorkoutCompleteDialog.value = false
                        onContinueRideAfterWorkoutComplete()
                    }
                ) {
                    Text(stringResource(R.string.session_workout_complete_reward_continue))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showWorkoutCompleteDialog.value = false
                        onEndSessionAfterWorkoutComplete()
                    }
                ) {
                    Text(stringResource(R.string.session_workout_complete_reward_summary))
                }
            },
        )
    }
}

@Composable
private fun SessionDataLayoutSection(
    selectedLayout: SessionDataLayoutMode,
    workoutRemainingValue: String,
    elapsedOfTotalValue: String,
    distanceValue: String,
    energyValue: String,
    speedValue: String,
    stepRemainingValue: String,
    stepTypeValue: String,
    primaryTopMetric: SessionTopMetricPresentation,
    secondaryTopMetric: SessionTopMetricPresentation?,
    compact: Boolean,
    phonePortraitLayout: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val cardBorder = sessionCardBorder()
    val metricSpacing = if (compact) 6.dp else 8.dp
    SectionCard(
        title = null,
        compact = compact,
        modifier = modifier,
        border = cardBorder,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metricSpacing),
        ) {
            TopMetricCard(
                label = primaryTopMetric.label,
                value = primaryTopMetric.value,
                modifier = Modifier
                    .weight(1f)
                    .sessionMetricSemantics(primaryTopMetric.stateDescription),
                border = cardBorder,
                compact = compact,
                emphasized = primaryTopMetric.emphasized,
                supportingText = primaryTopMetric.supportingText,
            )
            secondaryTopMetric?.let { metric ->
                TopMetricCard(
                    label = metric.label,
                    value = metric.value,
                    modifier = Modifier
                        .weight(1f)
                        .sessionMetricSemantics(metric.stateDescription),
                    border = cardBorder,
                    compact = compact,
                    emphasized = metric.emphasized,
                    supportingText = metric.supportingText,
                )
            }
        }

        when (selectedLayout) {
            SessionDataLayoutMode.FITNESS -> {
                if (phonePortraitLayout) {
                    SessionPortraitMetricRow(
                        startLabel = stringResource(R.string.session_workout_remaining),
                        startValue = workoutRemainingValue,
                        endLabel = stringResource(R.string.session_elapsed_of_total),
                        endValue = elapsedOfTotalValue,
                        border = cardBorder,
                        compact = compact,
                    )
                    SessionPortraitMetricRow(
                        startLabel = stringResource(R.string.summary_distance),
                        startValue = distanceValue,
                        endLabel = stringResource(R.string.session_energy_kcal_label),
                        endValue = energyValue,
                        border = cardBorder,
                        compact = compact,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(metricSpacing),
                    ) {
                        TopMetricCard(
                            label = stringResource(R.string.session_workout_remaining),
                            value = workoutRemainingValue,
                            modifier = Modifier.weight(1f),
                            border = cardBorder,
                        )
                        TopMetricCard(
                            label = stringResource(R.string.session_elapsed_of_total),
                            value = elapsedOfTotalValue,
                            modifier = Modifier.weight(1f),
                            border = cardBorder,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(metricSpacing),
                    ) {
                        TopMetricCard(
                            label = stringResource(R.string.summary_distance),
                            value = distanceValue,
                            modifier = Modifier.weight(1f),
                            border = cardBorder,
                        )
                        TopMetricCard(
                            label = stringResource(R.string.session_energy_kcal_label),
                            value = energyValue,
                            modifier = Modifier.weight(1f),
                            border = cardBorder,
                        )
                    }
                }
            }

            SessionDataLayoutMode.GOAL_ORIENTED -> {
                if (phonePortraitLayout) {
                    SessionPortraitMetricRow(
                        startLabel = stringResource(R.string.session_elapsed_of_total),
                        startValue = elapsedOfTotalValue,
                        endLabel = stringResource(R.string.session_workout_remaining),
                        endValue = workoutRemainingValue,
                        border = cardBorder,
                        compact = compact,
                    )
                    SessionPortraitMetricRow(
                        startLabel = stringResource(R.string.session_energy_kcal_label),
                        startValue = energyValue,
                        endLabel = stringResource(R.string.summary_distance),
                        endValue = distanceValue,
                        border = cardBorder,
                        compact = compact,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(metricSpacing),
                    ) {
                        TopMetricCard(
                            label = stringResource(R.string.session_elapsed_of_total),
                            value = elapsedOfTotalValue,
                            modifier = Modifier.weight(1f),
                            border = cardBorder,
                            compact = compact,
                        )
                        TopMetricCard(
                            label = stringResource(R.string.session_workout_remaining),
                            value = workoutRemainingValue,
                            modifier = Modifier.weight(1f),
                            border = cardBorder,
                            compact = compact,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(metricSpacing),
                    ) {
                        TopMetricCard(
                            label = stringResource(R.string.session_energy_kcal_label),
                            value = energyValue,
                            modifier = Modifier.weight(1f),
                            border = cardBorder,
                            compact = compact,
                        )
                        TopMetricCard(
                            label = stringResource(R.string.summary_distance),
                            value = distanceValue,
                            modifier = Modifier.weight(1f),
                            border = cardBorder,
                            compact = compact,
                        )
                    }
                }
            }
        }

    }
}

private fun Modifier.sessionMetricSemantics(description: String?): Modifier {
    if (description.isNullOrBlank()) return this
    return semantics {
        stateDescription = description
    }
}

internal fun resolveActiveImportedHrStep(
    workout: ImportedErgoWorkout?,
    runnerState: RunnerState,
): ImportedErgoWorkoutStep.HeartRateSteady? {
    if (runnerState.segmentType != RunnerSegmentType.HEART_RATE_STEADY) {
        return null
    }
    return workout?.steps
        ?.getOrNull(runnerState.sourceStepIndex ?: -1) as? ImportedErgoWorkoutStep.HeartRateSteady
}

@Composable
private fun SessionPortraitMetricRow(
    startLabel: String,
    startValue: String,
    endLabel: String,
    endValue: String,
    border: BorderStroke? = null,
    compact: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
    ) {
        TopMetricCard(
            label = startLabel,
            value = startValue,
            modifier = Modifier.weight(1f),
            border = border,
            compact = compact,
        )
        TopMetricCard(
            label = endLabel,
            value = endValue,
            modifier = Modifier.weight(1f),
            border = border,
            compact = compact,
        )
    }
}

@Composable
private fun SessionStateRail(
    sessionState: String,
    workoutTitle: String,
    workoutDescription: String?,
    mockTrainerModeEnabled: Boolean,
    compact: Boolean,
    portraitBaseline: Boolean,
    landscapeTwoPaneBaseline: Boolean,
    onOpenWorkoutInfo: () -> Unit,
    onEndSession: () -> Unit,
    endSessionEnabled: Boolean,
    endSessionCtaEmphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = null,
        compact = compact,
        modifier = modifier,
        border = sessionCardBorder(),
    ) {
        if (portraitBaseline) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.sm),
            ) {
                Text(
                    text = workoutTitle,
                    style = if (compact) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.headlineSmall
                    },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!workoutDescription.isNullOrBlank()) {
                    Text(
                        text = workoutDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SessionDenseEndButton(
                        onClick = onEndSession,
                        enabled = endSessionEnabled,
                        emphasized = endSessionCtaEmphasized,
                    )
                    Spacer(modifier = Modifier.width(UiSpacing.sm))
                    SessionStatusChip(status = sessionState)
                    if (!workoutDescription.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(UiSpacing.sm))
                        SessionInfoButton(
                            onClick = onOpenWorkoutInfo,
                            modifier = Modifier.size(if (compact) 28.dp else 32.dp),
                        )
                    }
                }
            }
        } else if (landscapeTwoPaneBaseline) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(UiSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = workoutTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!workoutDescription.isNullOrBlank()) {
                            Text(
                                text = workoutDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SessionStatusChip(status = sessionState)
                        if (!workoutDescription.isNullOrBlank()) {
                            SessionInfoButton(
                                onClick = onOpenWorkoutInfo,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        SessionDenseEndButton(
                            onClick = onEndSession,
                            enabled = endSessionEnabled,
                            emphasized = endSessionCtaEmphasized,
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = workoutTitle,
                        style = if (compact) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.headlineSmall
                        },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!workoutDescription.isNullOrBlank()) {
                        Text(
                            text = workoutDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SessionDenseEndButton(
                        onClick = onEndSession,
                        enabled = endSessionEnabled,
                        emphasized = endSessionCtaEmphasized,
                    )
                    SessionStatusChip(status = sessionState)
                    if (!workoutDescription.isNullOrBlank()) {
                        SessionInfoButton(
                            onClick = onOpenWorkoutInfo,
                            modifier = Modifier.size(if (compact) 28.dp else 32.dp),
                        )
                    }
                }
            }
        }
        if (mockTrainerModeEnabled) {
            Text(
                text = stringResource(R.string.session_mock_mode_active),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun SessionDenseTopRail(
    sessionState: String,
    workoutTitle: String,
    workoutDescription: String?,
    onOpenWorkoutInfo: () -> Unit,
    onEndSession: () -> Unit,
    endSessionEnabled: Boolean,
    endSessionCtaEmphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = null,
        compact = true,
        modifier = modifier.heightIn(min = SessionTopRailCompactHeight),
        border = sessionCardBorder(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = workoutTitle,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SessionStatusChip(status = sessionState)
                if (!workoutDescription.isNullOrBlank()) {
                    SessionInfoButton(
                        onClick = onOpenWorkoutInfo,
                        modifier = Modifier.size(24.dp),
                    )
                }
                SessionDenseEndButton(
                    onClick = onEndSession,
                    enabled = endSessionEnabled,
                    emphasized = endSessionCtaEmphasized,
                )
            }
        }
    }
}

@Composable
private fun SessionDenseEndButton(
    onClick: () -> Unit,
    enabled: Boolean,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = sessionQuitButtonColors(emphasized = emphasized),
        modifier = modifier
            .heightIn(min = 36.dp)
            .testTag("sessionQuitButton"),
    ) {
        Text(
            text = stringResource(R.string.session_end_button_compact),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SessionStatusChip(
    status: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape,
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SessionActionRail(
    onEndSession: () -> Unit,
    endSessionEnabled: Boolean,
    endSessionCtaEmphasized: Boolean,
    fullWidthButton: Boolean = false,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = null,
        compact = true,
        modifier = modifier,
        border = sessionCardBorder(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (fullWidthButton) {
                Arrangement.Center
            } else {
                Arrangement.End
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SessionEndButton(
                onClick = onEndSession,
                enabled = endSessionEnabled,
                emphasized = endSessionCtaEmphasized,
                modifier = if (fullWidthButton) {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("sessionQuitButton")
                } else {
                    Modifier
                        .widthIn(min = 220.dp)
                        .heightIn(min = 44.dp)
                        .testTag("sessionQuitButton")
                },
            )
        }
    }
}

@Composable
private fun SessionConnectivityStrip(
    ftmsReady: Boolean,
    ftmsControlGranted: Boolean,
    sessionIssues: List<String>,
    workoutExecutionModeMessage: String?,
    workoutExecutionModeIsError: Boolean,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = stringResource(R.string.session_status_strip_title),
        compact = true,
        modifier = modifier,
        border = sessionCardBorder(),
    ) {
        SessionStripStatusLine(
            text = stringResource(
                if (ftmsReady) {
                    R.string.session_status_connectivity_ready
                } else {
                    R.string.session_status_connectivity_waiting
                }
            ),
            ready = ftmsReady,
        )
        SessionStripStatusLine(
            text = stringResource(
                if (ftmsControlGranted) {
                    R.string.session_status_control_granted
                } else {
                    R.string.session_status_control_waiting
                }
            ),
            ready = ftmsControlGranted,
        )
        sessionIssues.forEach { issue ->
            Text(
                text = issue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (workoutExecutionModeIsError && !workoutExecutionModeMessage.isNullOrBlank()) {
            Text(
                text = workoutExecutionModeMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SessionStripStatusLine(
    text: String,
    ready: Boolean,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (ready) UiSemanticColor.setupReady else UiSemanticColor.setupPending,
    )
}

/**
 * Coaching/AI message panel that fills whatever vertical space is available.
 * Shows the highest-priority active message; empty card when nothing is queued.
 */
@Composable
private fun SessionMessagePanel(
    statusOverrideMessage: String?,
    workoutExecutionModeMessage: String?,
    workoutExecutionModeIsError: Boolean,
    aiAssistantMessage: String?,
    aiAssistantIsError: Boolean,
    workoutComplete: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val workoutCompleteLabel = stringResource(R.string.session_workout_complete)
    val entry = when {
        workoutExecutionModeIsError && !workoutExecutionModeMessage.isNullOrBlank() ->
            workoutExecutionModeMessage.trim() to true
        !aiAssistantMessage.isNullOrBlank() ->
            aiAssistantMessage.trim() to aiAssistantIsError
        !statusOverrideMessage.isNullOrBlank() ->
            statusOverrideMessage.trim() to false
        workoutComplete ->
            workoutCompleteLabel to false
        !workoutExecutionModeMessage.isNullOrBlank() ->
            workoutExecutionModeMessage.trim() to false
        else -> null
    }
    SectionCard(
        title = null,
        compact = compact,
        modifier = modifier,
        border = sessionCardBorder(),
    ) {
        if (entry != null) {
            Text(
                text = entry.first,
                style = if (compact) MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * 1.2f)
                        else MaterialTheme.typography.bodyLarge.copy(
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * 1.2f),
                color = if (entry.second) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
