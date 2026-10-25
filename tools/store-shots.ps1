# Captures the Play store screenshots with StoreShotsTest on one device and copies them into
# docs\listing\graphics. The phone fills the phone slot; the Galaxy Tab fills the 7-inch slot
# at its native density and the 10-inch slot at density 240, which is the same panel reporting
# a larger configuration, so every image is a real render at the geometry it claims.
#
#   powershell -File tools\store-shots.ps1                                   # Pixel 10
#   powershell -File tools\store-shots.ps1 -Serial emulator-5582 -Tablet                 # 7-inch slot
#   powershell -File tools\store-shots.ps1 -Serial emulator-5582 -Tablet -Density240     # 10-inch slot
#   powershell -File tools\store-shots.ps1 -Serial R52WB07WPAV -Tablet -CropBar          # the Galaxy Tab ignores demo mode
param(
  [string]$Serial = "adb-65150DLCR001R3-KVXECY._adb-tls-connect._tcp",
  [switch]$Tablet,
  [switch]$CropBar,
  [switch]$Density240,
  [switch]$SkipRun,
  [string]$Filter = "dev.peringer.limen.StoreShotsTest"
)
$ErrorActionPreference = "Continue"
$repo = Split-Path $PSScriptRoot -Parent
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" }
function Adb { & $adb -s $Serial @args }

$state = (& $adb -s $Serial get-state 2>&1)
if ($state -ne "device") { Write-Error "$Serial is not attached ($state)"; exit 1 }
$twice = (& $adb devices | Select-String "device$" | Measure-Object).Count
if ($twice -gt 1) { Write-Host "More than one transport is attached; the run is pinned to $Serial through ANDROID_SERIAL." }
if ($Density240 -and -not $Tablet) { Write-Error "-Density240 fills the 10-inch slot from the tablet; pass -Tablet"; exit 1 }

# Read the timeout before overwriting it, so the restore puts back what was there.
$timeout = (Adb shell settings get system screen_off_timeout).Trim()
Adb shell settings put system screen_off_timeout 1800000 | Out-Null
foreach ($scale in "window_animation_scale", "transition_animation_scale", "animator_duration_scale") { Adb shell settings put global $scale 0 | Out-Null }
# Demo mode: a clean 09:30, a full battery, no notification icons. Samsung ignores it; pass -CropBar there.
Adb shell settings put global sysui_demo_allowed 1 | Out-Null
Adb shell am broadcast -a com.android.systemui.demo -e command enter | Out-Null
Adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930 | Out-Null
Adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false | Out-Null
Adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e fully true | Out-Null
Adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false | Out-Null
if ($Density240) { Adb shell wm density 240 | Out-Null; Adb shell am get-config }

$code = 0
try {
  if (-not $SkipRun) {
    $env:ANDROID_SERIAL = $Serial
    Push-Location $repo
    & "$repo\gradlew.bat" :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$Filter" "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
    $code = $LASTEXITCODE
    Pop-Location
    if ($code -ne 0) { Write-Error "connectedDebugAndroidTest failed with $code" }
  }
  $tmp = Join-Path $env:TEMP ("limen-store-shots-" + [guid]::NewGuid().ToString("N").Substring(0, 8))
  New-Item -ItemType Directory -Path $tmp | Out-Null
  & $adb -s $Serial pull /sdcard/Android/data/dev.peringer.limen.uitest/files/store $tmp
  $pulled = Get-ChildItem -Path (Join-Path $tmp "store") -Directory
  foreach ($deviceDir in $pulled) {
    # Only where demo mode is ignored, which is the Samsung tablet; an emulator honours it.
    $crop = if ($CropBar) { if ($Density240) { 36 } else { 48 } } else { 0 }
    $contact = Join-Path $repo ("_scratch\store-shots-" + $deviceDir.Name + ".png")
    python -I "$repo\tools\store_shots_check.py" $deviceDir.FullName --into (Join-Path $repo ("docs\listing\graphics\" + $deviceDir.Name)) --crop-top $crop --contact $contact
    if ($LASTEXITCODE -ne 0) { $code = $LASTEXITCODE; Write-Error "the check refused $($deviceDir.Name)" }
  }
} finally {
  if ($Density240) { Adb shell wm density reset | Out-Null }
  Adb shell am broadcast -a com.android.systemui.demo -e command exit | Out-Null
  foreach ($scale in "window_animation_scale", "transition_animation_scale", "animator_duration_scale") { Adb shell settings put global $scale 1 | Out-Null }
  if ($timeout -match '^\d+$') { Adb shell settings put system screen_off_timeout $timeout | Out-Null }
}
exit $code
