#!/bin/bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
source "$project_root/scripts/mac-env.sh"

for tool in "$JAVA_HOME/bin/java" "$GOROOT/bin/go" "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"; do
    [[ -x "$tool" ]] || { echo "缺少外挂卷工具：$tool，请参阅 docs/RELEASE.md" >&2; exit 1; }
done
mkdir -p "$TMPDIR" "$LEES_CLASH_CACHE/java-home" "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME"
cd "$project_root"
printf 'sdk.dir=%s\nndk.dir=%s\n' "$ANDROID_HOME" "$ANDROID_NDK_HOME" > local.properties

if [[ $# -eq 0 ]]; then set -- :app:assembleAlphaDebug -Pandroid.injected.build.abi=arm64-v8a; fi
exec bash gradlew --no-daemon --console=plain \
    --project-cache-dir="$LEES_CLASH_CACHE/project-cache" \
    -Pkotlin.project.persistent.dir="$LEES_CLASH_CACHE/kotlin" \
    -Pkotlin.project.persistent.dir.gradle.disableWrite=true "$@"
