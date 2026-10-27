# Source this file for builds, sdkmanager, adb, or emulator on Apple Silicon.
# All downloaded tools, user state and shared caches stay on the external volume.
export LEES_CLASH_TOOLCHAIN=/Volumes/DISK/Applications/lees-clash-toolchain
export LEES_CLASH_CACHE=/Volumes/DISK/DevCache/lees-clash
export JAVA_HOME="$LEES_CLASH_TOOLCHAIN/jdk-21/Contents/Home"
export GOROOT="$LEES_CLASH_TOOLCHAIN/go"
export GOTOOLCHAIN=local
export GOPATH="$LEES_CLASH_CACHE/go"
export GOCACHE="$LEES_CLASH_CACHE/go-build"
export GOMODCACHE="$LEES_CLASH_CACHE/go-mod"
export TMPDIR="$LEES_CLASH_CACHE/tmp"
export TMP="$TMPDIR" TEMP="$TMPDIR" GOTMPDIR="$TMPDIR"
export GRADLE_USER_HOME="$LEES_CLASH_CACHE/gradle"
export ANDROID_HOME="$LEES_CLASH_TOOLCHAIN/android-sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/29.0.14206865"
export ANDROID_USER_HOME="$LEES_CLASH_CACHE/android-user"
export ANDROID_EMULATOR_HOME="$ANDROID_USER_HOME"
export ANDROID_AVD_HOME="$LEES_CLASH_CACHE/android-avd"
export ADB_VENDOR_KEYS="$ANDROID_USER_HOME"
export JAVA_TOOL_OPTIONS="-Duser.home=$LEES_CLASH_CACHE/java-home -Djava.io.tmpdir=$TMPDIR"
export PATH="$JAVA_HOME/bin:$GOROOT/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
