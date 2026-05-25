#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${SMOKE_TEST_CLASSES:-}" ]]; then
  echo "error: SMOKE_TEST_CLASSES is required"
  exit 1
fi

ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"
SMOKE_INCLUDE_FLAKY="${SMOKE_INCLUDE_FLAKY:-false}"
SMOKE_LOG_PREFIX="${SMOKE_LOG_PREFIX:-smoke-run}"
SMOKE_RETRY_SUMMARY_PATH="${SMOKE_RETRY_SUMMARY_PATH:-smoke-retry-summary.txt}"
SMOKE_CLASSIFICATION_SUMMARY_PATH="${SMOKE_CLASSIFICATION_SUMMARY_PATH:-smoke-classification-summary.txt}"
SMOKE_ATTEMPT_TIMEOUT_SECONDS="${SMOKE_ATTEMPT_TIMEOUT_SECONDS:-1200}"

attempt_one_log="${SMOKE_LOG_PREFIX}.attempt1.log"
attempt_two_log="${SMOKE_LOG_PREFIX}.attempt2.log"
final_log="${SMOKE_LOG_PREFIX}.log"

# Keep the emulator readiness checks centralized so retries reuse the same safety gates.
prepare_emulator() {
  adb -s "${ANDROID_SERIAL}" wait-for-device
  adb -s "${ANDROID_SERIAL}" reconnect offline || true

  for i in $(seq 1 120); do
    boot="$(adb -s "${ANDROID_SERIAL}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    pkg="$(adb -s "${ANDROID_SERIAL}" shell service check package 2>/dev/null || true)"
    if [[ "${boot}" == "1" ]] && echo "${pkg}" | grep -q "found"; then
      echo "emulator package service ready"
      break
    fi

    if [[ "${i}" -eq 120 ]]; then
      echo "error: emulator package service not ready"
      return 1
    fi
    sleep 2
  done

  adb -s "${ANDROID_SERIAL}" shell settings put global window_animation_scale 0.0 || echo "warning: failed to set window_animation_scale"
  adb -s "${ANDROID_SERIAL}" shell settings put global transition_animation_scale 0.0 || echo "warning: failed to set transition_animation_scale"
  adb -s "${ANDROID_SERIAL}" shell settings put global animator_duration_scale 0.0 || echo "warning: failed to set animator_duration_scale"

  for i in $(seq 1 60); do
    state="$(adb -s "${ANDROID_SERIAL}" get-state 2>/dev/null || true)"
    if [[ "${state}" == "device" ]]; then
      break
    fi

    adb -s "${ANDROID_SERIAL}" reconnect offline || true
    if [[ "${i}" -eq 60 ]]; then
      echo "error: emulator never reached online device state"
      adb devices -l
      return 1
    fi
    sleep 2
  done

  for i in $(seq 1 30); do
    if adb -s "${ANDROID_SERIAL}" shell cmd package list packages >/dev/null 2>&1; then
      break
    fi

    if [[ "${i}" -eq 30 ]]; then
      echo "error: package manager cmd unavailable"
      adb -s "${ANDROID_SERIAL}" shell service check package || true
      return 1
    fi
    sleep 2
  done
}

run_smoke_attempt() {
  local attempt="$1"
  local log_file="$2"

  local gradle_args=(
    :app:connectedDebugAndroidTest
    --no-daemon
    --stacktrace
    "-Pandroid.testInstrumentationRunnerArguments.class=${SMOKE_TEST_CLASSES}"
  )

  if [[ "${SMOKE_INCLUDE_FLAKY}" != "true" ]]; then
    gradle_args+=("-Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.FlakyTest")
  fi

  echo "Running instrumentation smoke attempt ${attempt} (include_flaky=${SMOKE_INCLUDE_FLAKY})"
  set +e
  timeout --signal=TERM --kill-after=30s "${SMOKE_ATTEMPT_TIMEOUT_SECONDS}" \
    env ANDROID_SERIAL="${ANDROID_SERIAL}" ./gradlew "${gradle_args[@]}" 2>&1 | tee "${log_file}"
  local gradle_rc=${PIPESTATUS[0]}
  set -e

  if [[ "${gradle_rc}" -eq 124 ]]; then
    echo "SMOKE_ATTEMPT_TIMEOUT: ${SMOKE_ATTEMPT_TIMEOUT_SECONDS}s exceeded on attempt ${attempt}" | tee -a "${log_file}"
  fi

  return "${gradle_rc}"
}

is_infra_failure() {
  local log_file="$1"

  if grep -Eiq \
    "ShellCommandUnresponsiveException|SMOKE_ATTEMPT_TIMEOUT|device offline|INSTRUMENTATION_ABORTED|emulator package service not ready|emulator never reached online device state|package manager cmd unavailable|No connected devices|Unable to connect to adb daemon|adb: failed|Can't find service: package|Can't find service: activity|Failed to install split APK\\(s\\)|Failed to install-write all apks|DELETE_FAILED_INTERNAL_ERROR" \
    "${log_file}"; then
    return 0
  fi

  # AGP can surface emulator/install crashes as report parsing failures after zero tests run.
  if grep -Eq "Starting 0 tests on emulator" "${log_file}" \
    && grep -Eq "Could not load test results from '.*/TEST-emulator-.*\\.xml'" "${log_file}" \
    && grep -Eq "CompositeTestResults\\.addStandardError|TestReport\\.mergeFromFile" "${log_file}"; then
    return 0
  fi

  return 1
}

record_summary() {
  local final_result="$1"
  local attempts="$2"
  local infra_retry_triggered="$3"

  {
    echo "event_name=${GITHUB_EVENT_NAME:-unknown}"
    echo "include_flaky=${SMOKE_INCLUDE_FLAKY}"
    echo "attempt_timeout_seconds=${SMOKE_ATTEMPT_TIMEOUT_SECONDS}"
    echo "attempts=${attempts}"
    echo "infra_retry_triggered=${infra_retry_triggered}"
    echo "final_result=${final_result}"
  } > "${SMOKE_RETRY_SUMMARY_PATH}"

  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    {
      echo "### Instrumentation Smoke Retry Summary"
      echo
      echo "- event_name=${GITHUB_EVENT_NAME:-unknown}"
      echo "- include_flaky=${SMOKE_INCLUDE_FLAKY}"
      echo "- attempt_timeout_seconds=${SMOKE_ATTEMPT_TIMEOUT_SECONDS}"
      echo "- attempts=${attempts}"
      echo "- infra_retry_triggered=${infra_retry_triggered}"
      echo "- final_result=${final_result}"
    } >> "${GITHUB_STEP_SUMMARY}"
  fi
}

record_classification_summary() {
  local final_result="$1"
  local attempts="$2"
  local infra_retry_triggered="$3"

  local attempt_one_infra="false"
  local attempt_two_infra="false"
  local infra_noise="false"
  local infra_retry="false"
  local functional_failure="false"

  if [[ -f "${attempt_one_log}" ]] && is_infra_failure "${attempt_one_log}"; then
    attempt_one_infra="true"
  fi
  if [[ -f "${attempt_two_log}" ]] && is_infra_failure "${attempt_two_log}"; then
    attempt_two_infra="true"
  fi

  if [[ "${infra_retry_triggered}" == "true" ]]; then
    infra_retry="true"
  fi

  if [[ "${final_result}" == "success" ]] && [[ "${attempt_one_infra}" == "true" || "${attempt_two_infra}" == "true" ]]; then
    infra_noise="true"
  fi

  if [[ "${final_result}" == "failure" ]]; then
    if [[ "${attempts}" -eq 1 ]] && [[ "${attempt_one_infra}" != "true" ]]; then
      functional_failure="true"
    elif [[ "${attempts}" -eq 2 ]] && [[ "${attempt_two_infra}" != "true" ]]; then
      functional_failure="true"
    fi
  fi

  {
    echo "event_name=${GITHUB_EVENT_NAME:-unknown}"
    echo "attempts=${attempts}"
    echo "final_result=${final_result}"
    echo "attempt_one_infra_markers=${attempt_one_infra}"
    echo "attempt_two_infra_markers=${attempt_two_infra}"
    echo "infra_noise=${infra_noise}"
    echo "infra_retry=${infra_retry}"
    echo "functional_failure=${functional_failure}"
  } > "${SMOKE_CLASSIFICATION_SUMMARY_PATH}"

  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    {
      echo "### Instrumentation Smoke Classification Summary"
      echo
      echo "- infra_noise=${infra_noise}"
      echo "- infra_retry=${infra_retry}"
      echo "- functional_failure=${functional_failure}"
    } >> "${GITHUB_STEP_SUMMARY}"
  fi
}

echo "sdk.dir=${ANDROID_SDK_ROOT}" > local.properties

attempts=1
infra_retry_triggered=false
final_rc=0
final_result="success"

if prepare_emulator && run_smoke_attempt 1 "${attempt_one_log}"; then
  cp "${attempt_one_log}" "${final_log}"
else
  final_rc=$?
  final_result="failure"

  if [[ -f "${attempt_one_log}" ]] && is_infra_failure "${attempt_one_log}"; then
    infra_retry_triggered=true
    attempts=2
    echo "Detected infrastructure failure markers, retrying instrumentation smoke once"

    if prepare_emulator && run_smoke_attempt 2 "${attempt_two_log}"; then
      final_rc=0
      final_result="success"
    else
      final_rc=$?
      final_result="failure"
    fi
  fi

  if [[ "${attempts}" -eq 2 && -f "${attempt_two_log}" ]]; then
    cp "${attempt_two_log}" "${final_log}"
  else
    cp "${attempt_one_log}" "${final_log}"
  fi
fi

record_summary "${final_result}" "${attempts}" "${infra_retry_triggered}"
record_classification_summary "${final_result}" "${attempts}" "${infra_retry_triggered}"
exit "${final_rc}"
