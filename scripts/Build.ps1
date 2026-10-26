param(
    [string[]]$Tasks = @(':app:assembleAlphaDebug'),
    [ValidateSet('x86_64','arm64-v8a','armeabi-v7a','x86','all')][string]$Abi = 'x86_64',
    [string]$SdkRoot = 'I:\DevCache\android-sdk',
    [string]$JdkRoot = 'I:\DevCache\jdk21'
)
$ErrorActionPreference = 'Stop'
$env:TEMP = 'I:\DevCache\Temp'
$env:TMP = $env:TEMP
$env:GRADLE_USER_HOME = 'I:\DevCache\gradle'
$env:ANDROID_USER_HOME = 'I:\DevCache\android-user'
$env:ANDROID_AVD_HOME = 'I:\DevCache\android-avd'
$env:GOCACHE = 'I:\DevCache\go-build'
$env:GOMODCACHE = 'I:\DevCache\go-mod'
$env:GOTMPDIR = $env:TEMP
$env:JAVA_HOME = $JdkRoot
$env:JAVA_TOOL_OPTIONS = "$env:JAVA_TOOL_OPTIONS -Duser.home=I:/DevCache/java-home".Trim()
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_NDK_HOME = Join-Path $SdkRoot 'ndk\29.0.14206865'
$env:PATH = "$JdkRoot\bin;I:\DevCache\go1.26\bin;$env:PATH"
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $sdkPath = $SdkRoot.Replace('\','/')
    $ndkPath = $env:ANDROID_NDK_HOME.Replace('\','/')
    @("sdk.dir=$sdkPath", "ndk.dir=$ndkPath") | Set-Content -LiteralPath local.properties -Encoding utf8
    $arguments = @('--no-daemon', '--project-cache-dir=I:\DevCache\lees-clash-project-cache',
        '-Pkotlin.project.persistent.dir=I:/DevCache/lees-clash-kotlin',
        '-Pkotlin.project.persistent.dir.gradle.disableWrite=true') + $Tasks
    if ($Abi -ne 'all') { $arguments += "-Pandroid.injected.build.abi=$Abi" }
    & .\gradlew.bat @arguments
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
} finally { Pop-Location }
