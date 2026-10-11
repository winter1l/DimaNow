#!/usr/bin/env bash
# Runs one AndroidJUnitRunner shard of :app:connectedDebugAndroidTest on the booted CI emulator.
#
# Long emulator runs have crashed system_server mid-suite, so a failing shard is rerun once
# in full (not only the failed classes: a crashed run never reports the tests after the crash).
# A stale assertion fails both attempts and fails the job; a pass on the rerun is reported
# as a warning annotation and both attempts' reports are kept for upload.
#
# Inputs (environment): SHARD_COUNT, SHARD_INDEX. Called from .github/workflows/instrumentation.yml.
set -uo pipefail

shard_count="${SHARD_COUNT:?SHARD_COUNT is required}"
shard_index="${SHARD_INDEX:?SHARD_INDEX is required}"
case "$shard_count:$shard_index" in
  *[!0-9:]*|:*|*:) echo "SHARD_COUNT and SHARD_INDEX must be non-negative integers" >&2; exit 2 ;;
esac

results_dir="app/build/outputs/androidTest-results/connected"
reports_dir="app/build/reports/androidTests/connected"
attempts_dir="app/build/outputs/androidTest-attempts"

prepare_device() {
  adb wait-for-device
  local waited=0
  until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    if [ "$waited" -ge 300 ]; then echo "Emulator did not finish booting" >&2; return 1; fi
    sleep 5
    waited=$((waited + 5))
  done
  # Compose tests find no hierarchy while the screen is off or the keyguard is shown.
  adb shell input keyevent KEYCODE_WAKEUP
  adb shell wm dismiss-keyguard
  adb shell svc power stayon true
  adb shell settings put system screen_off_timeout 2147483647
}

run_shard() {
  bash ./gradlew :app:connectedDebugAndroidTest --console=plain \
    -Pandroid.testInstrumentationRunnerArguments.numShards="$shard_count" \
    -Pandroid.testInstrumentationRunnerArguments.shardIndex="$shard_index"
}

save_attempt() {
  mkdir -p "$attempts_dir/$1"
  [ -d "$results_dir" ] && cp -R "$results_dir" "$attempts_dir/$1/results"
  [ -d "$reports_dir" ] && cp -R "$reports_dir" "$attempts_dir/$1/reports"
  return 0
}

failed_classes() {
  python3 - "$results_dir" <<'PY'
import pathlib, sys, xml.etree.ElementTree as ET
failed = set()
for path in pathlib.Path(sys.argv[1]).rglob("*.xml"):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    for case in root.iter("testcase"):
        if case.find("failure") is not None or case.find("error") is not None:
            failed.add(case.get("classname", "?"))
print(", ".join(sorted(failed)) or "none recorded (instrumentation crash?)")
PY
}

prepare_device || exit 1
run_shard
status=$?
if [ "$status" -eq 0 ]; then exit 0; fi

save_attempt first
first_failures="$(failed_classes)"
echo "::warning title=Instrumentation shard $shard_index failed::First attempt failed: $first_failures. Rerunning the shard once."
if [ -n "${GITHUB_OUTPUT:-}" ]; then echo "rerun=true" >> "$GITHUB_OUTPUT"; fi

prepare_device || exit 1
run_shard
status=$?
save_attempt rerun
if [ "$status" -eq 0 ]; then
  echo "::warning title=Flaky instrumentation (shard $shard_index)::Passed only on rerun. First-attempt failures: $first_failures"
  exit 0
fi
echo "::error title=Instrumentation shard $shard_index failed twice::Rerun failures: $(failed_classes)"
exit "$status"
