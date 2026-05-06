param(
    [string]$ProjectRoot = "e:\photos and works\py\r2-image-bed-android"
)

$ErrorActionPreference = "Stop"

$downloadsDir = Join-Path $ProjectRoot ".downloads"
$sdkRoot = Join-Path $ProjectRoot "android-sdk"
$cmdlineZip = Join-Path $downloadsDir "commandlinetools-win-14742923_latest.zip"
$gradleZip = Join-Path $downloadsDir "gradle-8.7-bin.zip"
$cmdlineExtractRoot = Join-Path $sdkRoot "cmdline-tools"
$cmdlineLatestDir = Join-Path $cmdlineExtractRoot "latest"
$cmdlineTempDir = Join-Path $cmdlineExtractRoot "temp"

if (!(Test-Path $cmdlineZip)) {
    throw "Missing required file: $cmdlineZip"
}

if (!(Test-Path $gradleZip)) {
    Write-Warning "Missing optional file: $gradleZip. Gradle may try to download online later."
}

New-Item -ItemType Directory -Force -Path $sdkRoot | Out-Null
New-Item -ItemType Directory -Force -Path $cmdlineExtractRoot | Out-Null

if (Test-Path $cmdlineLatestDir) {
    Remove-Item $cmdlineLatestDir -Recurse -Force
}
if (Test-Path $cmdlineTempDir) {
    Remove-Item $cmdlineTempDir -Recurse -Force
}

Expand-Archive -Path $cmdlineZip -DestinationPath $cmdlineTempDir -Force
$extractedCmdlineDir = Join-Path $cmdlineTempDir "cmdline-tools"
New-Item -ItemType Directory -Force -Path $cmdlineLatestDir | Out-Null
Copy-Item -Path (Join-Path $extractedCmdlineDir "*") -Destination $cmdlineLatestDir -Recurse -Force
Remove-Item $cmdlineTempDir -Recurse -Force

$sdkManager = Join-Path $cmdlineLatestDir "bin\sdkmanager.bat"
if (!(Test-Path $sdkManager)) {
    throw "sdkmanager.bat not found: $sdkManager"
}

$licenseArgs = @("--sdk_root=$sdkRoot", "--licenses")
$packageArgs = @(
    "--sdk_root=$sdkRoot",
    "platform-tools",
    "platforms;android-35",
    "build-tools;35.0.0"
)

@("y", "y", "y", "y", "y", "y") | & $sdkManager @licenseArgs | Out-Null
& $sdkManager @packageArgs

Write-Output "Android SDK is ready: $sdkRoot"