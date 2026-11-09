# Puts the TV back exactly how Google shipped it (Windows).
#
# Easiest: double-click Restore-Windows.cmd in this folder.
# Or from PowerShell:
#   powershell -ExecutionPolicy Bypass -File restore.ps1
#   powershell -ExecutionPolicy Bypass -File restore.ps1 -Tv 192.168.4.40:5555 -KeepApp
param(
    [string]$Tv = "192.168.4.40",
    [switch]$KeepApp
)

$ErrorActionPreference = "Continue"
if ($Tv -notmatch ":") { $Tv = "${Tv}:5555" }

$Here = $PSScriptRoot
$Pkg = "com.sistema.home"
# Always restored, even if the backup folder is gone.
$Always = @("com.google.android.apps.tv.launcherx", "com.google.android.tungsten.setupwraith")
$Fallback = @(
    "com.google.android.tvrecommendations", "com.google.android.videos", "com.google.android.youtube.tvmusic",
    "com.google.android.youtube.tvkids", "com.google.android.play.games", "com.google.android.feedback",
    "com.android.printspooler", "tv.pluto.android", "com.tubitv", "com.google.android.apps.tv.dreamx"
)

function Step([string]$text) {
    Write-Host ""
    Write-Host ":: " -ForegroundColor DarkYellow -NoNewline
    Write-Host $text
}
function Ok([string]$text) { Write-Host "   $text" }
function Fail([string]$text) {
    Write-Host ""
    Write-Host $text -ForegroundColor Red
    exit 1
}

$Adb = $null
$found = Get-Command adb -ErrorAction SilentlyContinue
if ($found) { $Adb = $found.Path }
$LocalAdb = Join-Path $Here "tools\platform-tools\adb.exe"
if (-not $Adb -and (Test-Path $LocalAdb)) { $Adb = $LocalAdb }
if (-not $Adb) { Fail "adb isn't here yet. Run Setup-Windows.cmd once first (it downloads adb), or install Platform Tools." }

function TvShell {
    $out = & $Adb -s $Tv shell @args 2>$null | Out-String
    return ($out -replace "`r", "")
}

Step "Connecting to $Tv"
$connected = $false
for ($i = 1; $i -le 12; $i++) {
    & $Adb connect $Tv 2>$null | Out-Null
    $state = (& $Adb -s $Tv get-state 2>$null | Out-String).Trim()
    if ($state -eq "device") { $connected = $true; break }
    Start-Sleep -Seconds 5
}
if (-not $connected) { Fail "Couldn't connect to $Tv. Check Developer options on the TV and that you're on the same Wi-Fi." }

$Serial = (TvShell getprop ro.serialno).Trim()
if (-not $Serial) { $Serial = "tv" }
$Record = Join-Path $Here "backups\$Serial\disabled-by-sistema.txt"

$pkgs = @($Always)
if ((Test-Path $Record) -and ((Get-Item $Record).Length -gt 0)) {
    Ok "Using the record from setup: backups\$Serial\"
    $pkgs += @(Get-Content -Path $Record | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" })
} else {
    Ok "No setup record found for this TV. Re-enabling the standard list."
    $pkgs += $Fallback
}

Step "Turning everything back on"
$seen = @{}
foreach ($p in $pkgs) {
    if ($seen.ContainsKey($p)) { continue }
    $seen[$p] = $true
    $res = TvShell pm enable --user 0 $p
    if ($res -match "new state: enabled") { Ok "On: $p" } else { Ok "Not on this TV: $p" }
}

if ($KeepApp) {
    Step "Keeping Sistema Home installed"
    Ok "Press Home. If the TV asks which home screen to use, pick Google TV Home."
} else {
    Step "Removing Sistema Home"
    $out = & $Adb -s $Tv uninstall $Pkg 2>&1 | ForEach-Object { "$_" } | Out-String
    if ($out -match "Success") { Ok "Removed." } else { Ok "It wasn't installed." }
}

if (Test-Path $Record) {
    Move-Item -Path $Record -Destination ($Record + ".restored-" + (Get-Date -Format "yyyyMMdd-HHmmss")) -Force
}
TvShell input keyevent KEYCODE_HOME | Out-Null

Step "Done. Your TV is back to stock."
