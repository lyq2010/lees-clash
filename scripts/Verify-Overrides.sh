#!/bin/bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
source "$project_root/scripts/mac-env.sh"
serial="${1:?请指定专用ARM64模拟器序列号}"
[[ "$serial" == emulator-* ]] || { echo "仅用于专用模拟器" >&2; exit 1; }
[[ "$(adb -s "$serial" shell getprop ro.product.cpu.abi | tr -d '\r')" == arm64-v8a ]] || exit 1
export GOOS=android GOARCH=arm64 CGO_ENABLED=1
export CC="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android26-clang"
cd "$project_root/core/src/main/golang"
go test -tags=foss,with_gvisor,cmfa -c -o "$LEES_CLASH_CACHE/override-config.test" ./native/config
adb -s "$serial" push "$LEES_CLASH_CACHE/override-config.test" /data/local/tmp/lees-clash-override.test >/dev/null
log="$LEES_CLASH_CACHE/override-native-$serial.log"
adb -s "$serial" shell 'TMPDIR=/data/local/tmp /data/local/tmp/lees-clash-override.test -test.v' > "$log" 2>&1
grep -q '^PASS' "$log"
echo "Native override combinations PASS ($log)"
