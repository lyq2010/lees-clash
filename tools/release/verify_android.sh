#!/usr/bin/env bash
set -euo pipefail
test "${GITHUB_ACTIONS:-}" = true
mode="${1:?Choose debug or release}"
[[ "$mode" = debug || "$mode" = release ]]
export ANDROID_AVD_HOME="$RUNNER_TEMP/clash-ci-avd"
export ANDROID_USER_HOME="$RUNNER_TEMP/clash-android-user"
export ANDROID_EMULATOR_HOME="$ANDROID_USER_HOME"
mkdir -p "$ANDROID_AVD_HOME" "$RUNNER_TEMP/clash-verification"
serial=emulator-5580
if [[ "$mode" = release ]]; then serial=emulator-5582; fi
python3 scripts/local-fixture.py > "$RUNNER_TEMP/clash-verification/fixture.log" 2>&1 &
fixture_pid=$!
cleanup() {
  local status=$?
  if [[ "$status" != 0 ]]; then
    timeout 15 adb -s "$serial" logcat -d -b crash -v threadtime > "$RUNNER_TEMP/clash-verification/$mode-android-crash.log" 2>&1 || true
    timeout 15 adb -s "$serial" shell dumpsys activity exit-info io.github.leenban.clash > "$RUNNER_TEMP/clash-verification/$mode-process-exit.log" 2>&1 || true
    timeout 15 adb -s "$serial" shell dumpsys meminfo > "$RUNNER_TEMP/clash-verification/$mode-guest-memory.log" 2>&1 || true
    free -m > "$RUNNER_TEMP/clash-verification/$mode-host-memory.log" 2>&1 || true
    ps -eo pid,comm,rss --sort=-rss | head -15 > "$RUNNER_TEMP/clash-verification/$mode-host-processes.log" || true
  fi
  adb -s "$serial" emu kill >/dev/null 2>&1 || true
  kill "$fixture_pid" >/dev/null 2>&1 || true
}
trap cleanup EXIT
boot() {
  local name="$1"
  printf 'no\n' | avdmanager create avd -n "$name" -k 'system-images;android-35;google_apis;x86_64' -d pixel_2
  if [ ! -f "$ANDROID_AVD_HOME/$name.ini" ]; then
    printf 'avd.ini.encoding=UTF-8\npath=%s/%s.avd\ntarget=android-35\n' "$ANDROID_AVD_HOME" "$name" > "$ANDROID_AVD_HOME/$name.ini"
  fi
  emulator -avd "$name" -no-window -no-snapshot -no-audio -no-metrics -no-boot-anim -gpu swiftshader -memory 2048 -port "${serial#emulator-}" > "$RUNNER_TEMP/clash-verification/$name-emulator.log" 2>&1 &
  for attempt in $(seq 1 150); do
    if [ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; then
      adb -s "$serial" shell input keyevent 82
      adb -s "$serial" logcat -c || true
      free -m > "$RUNNER_TEMP/clash-verification/$mode-host-memory-before.log"
      return
    fi
    sleep 2
  done
  echo "Android emulator failed to boot" >&2
  exit 1
}
if [[ "$mode" = debug ]]; then
  boot clash-debug
  adb -s "$serial" install -r -t "$(find app/build -type f -name '*.apk' -path '*/apk/alpha/debug/*' -print -quit)"
  adb -s "$serial" install -r -t "$(find app/build -type f -name '*.apk' -path '*/apk/androidTest/alpha/debug/*' -print -quit)"
  adb -s "$serial" shell appops set io.github.leenban.clash ACTIVATE_VPN allow
  adb -s "$serial" shell pm grant io.github.leenban.clash android.permission.POST_NOTIFICATIONS
  for port in 18080 18081 13053; do adb -s "$serial" reverse "tcp:$port" "tcp:$port"; done
  # JUnit method order is unspecified; the integration suite needs this local fixture first.
  adb -s "$serial" shell am instrument -w -e class 'io.github.leenban.clash.lucid.NativeIntegrationTest#prepareLocalProfile' io.github.leenban.clash.test/androidx.test.runner.AndroidJUnitRunner | tee "$RUNNER_TEMP/clash-verification/prepare.txt"
  grep -Eq '^OK \([0-9]+ tests?\)' "$RUNNER_TEMP/clash-verification/prepare.txt"
  classes=(NativeIntegrationTest BinderCancellationTest HomeUiTest FeedbackUiTest ProfileWorkflowTest DiagnosticPagingTest DiagnosticLifecycleTest NavigationSmokeTest ExtendedUiVerificationTest DeviceFeatureVerificationTest MemoryRegressionTest LocalBackupVerificationTest ProfileExtensionVerificationTest NetworkFeatureVerificationTest AdvancedUiVerificationTest StressVerificationTest EncryptedProfileVerificationTest SystemEntryVerificationTest SystemDocumentWorkflowTest OverrideCombinationsVerificationTest SubscriptionDetailsVerificationTest DnsFailureVerificationTest HomeEntryVerificationTest ConnectionIdentityVerificationTest ExternalResourceVerificationTest MemoryFootprintTest NodeLayoutVerificationTest NotificationNavigationTest PolicyIconVerificationTest SmartModeVerificationTest UiRefinementTest)
  failed=0
  for class in "${classes[@]}"; do
    log="$RUNNER_TEMP/clash-verification/$class.txt"
    adb -s "$serial" shell am instrument -w -e class "io.github.leenban.clash.lucid.$class" io.github.leenban.clash.test/androidx.test.runner.AndroidJUnitRunner | tee "$log"
    if ! grep -Eq '^OK \([0-9]+ tests?\)' "$log"; then failed=1; fi
  done
  test "$failed" -eq 0
else
  boot clash-release
  release_apk="$(find app/build/outputs/apk/alpha/release -name '*.apk' -print -quit)"
  test "$(find app/build/outputs/apk/alpha/release -name '*.apk' | wc -l)" -eq 1
  adb -s "$serial" install "$release_apk"
  python3 "${LEES_CLASH_RELEASE_SMOKE:-tools/release/release_smoke.py}" --serial "$serial"
fi
