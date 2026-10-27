#!/bin/bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
source "$project_root/scripts/mac-env.sh"
serial="${1:?请指定已准备好本地测试配置的专用模拟器序列号}"
[[ "$serial" == emulator-* ]] || { echo "此脚本仅用于专用模拟器" >&2; exit 1; }
restore_display() {
    adb -s "$serial" shell settings put system font_scale 1.0 >/dev/null
    adb -s "$serial" shell wm size reset >/dev/null
    adb -s "$serial" shell wm density reset >/dev/null
}
trap restore_display EXIT
failure=0
while IFS=: read -r label dimensions density font; do
    # adb shell otherwise consumes subsequent cases from the loop's stdin.
    {
        adb -s "$serial" shell am force-stop io.github.leenban.clash
        adb -s "$serial" shell wm size "$dimensions"
        adb -s "$serial" shell wm density "$density"
        adb -s "$serial" shell settings put system font_scale "$font"
        log="$LEES_CLASH_CACHE/layout-$label.log"
        adb -s "$serial" shell am instrument -w -r \
            -e class 'io.github.leenban.clash.lucid.LayoutVerificationTest,io.github.leenban.clash.lucid.NavigationSmokeTest' \
            -e layoutLabel "$label" io.github.leenban.clash.test/androidx.test.runner.AndroidJUnitRunner > "$log" 2>&1
        if grep -Eq '^OK \(' "$log"; then echo "$label PASS"; else echo "$label FAIL ($log)"; failure=1; fi
    } </dev/null
done <<'CASES'
phone-portrait:1080x2340:440:1.0
phone-landscape:2340x1080:440:1.0
phone-large:1080x2340:480:2.0
phone-landscape-large:2340x1080:440:2.0
tablet-portrait:1600x2560:240:1.0
tablet-landscape:2560x1600:240:1.0
tablet-large:1600x2560:240:2.0
tablet-landscape-large:2560x1600:240:2.0
CASES
exit "$failure"
