# Run with Android Studio CLOSED (or after stopping Gradle sync).
# Usage: powershell -ExecutionPolicy Bypass -File scripts\clean-android-build.ps1

$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

Write-Host "Stopping Gradle daemons..."
& .\gradlew.bat --stop

Start-Sleep -Seconds 2

$dirs = @(
    "shared\build",
    "androidApp\build",
    ".gradle\8.9\executionHistory"
)

foreach ($d in $dirs) {
    if (Test-Path $d) {
        Write-Host "Removing $d ..."
        Remove-Item -Recurse -Force $d -ErrorAction SilentlyContinue
        if (Test-Path $d) {
            Write-Host "WARN: Could not delete $d (file locked). Close Android Studio and retry." -ForegroundColor Yellow
        }
    }
}

Write-Host "Building debug APK..."
& .\gradlew.bat :androidApp:assembleDebug --no-daemon
