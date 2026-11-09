# Sistema Home setup for Windows.
#
# Easiest: double-click Setup-Windows.cmd in this folder.
# Or from PowerShell:
#   powershell -ExecutionPolicy Bypass -File setup.ps1
#   powershell -ExecutionPolicy Bypass -File setup.ps1 -Tv 192.168.4.40:5555 -Yes -Ambient
#
# Everything is disabled, never deleted. Restore-Windows.cmd puts it all back.
param(
    [string]$Tv = "192.168.4.40",
    [switch]$Yes,
    [switch]$Ambient,
    [string]$Name = "Nicolas",
    [string]$City = "Miami",
    [string]$Lat = "25.7617",
    [string]$Lon = "-80.1918",
    [string]$Units = "f"
)

$ErrorActionPreference = "Continue"
$ProgressPreference = "SilentlyContinue"
if ($Tv -notmatch ":") { $Tv = "${Tv}:5555" }

$Here = $PSScriptRoot
$Apk = Join-Path $Here "dist\sistema-home.apk"
$Pkg = "com.sistema.home"
$HomeActivity = "$Pkg/.HomeActivity"

# The stock Google TV home, and the setup app that grabs Home when the launcher is off.
$LauncherPkgs = @("com.google.android.apps.tv.launcherx", "com.google.android.tungsten.setupwraith")

# Ads, recommendation feeds and preloaded apps you don't use. Only touched if present.
$Bloat = @(
    "com.google.android.tvrecommendations|Sponsored rows and recommendation feed",
    "com.google.android.videos|Google TV movie store",
    "com.google.android.youtube.tvmusic|YouTube Music (you're on Spotify)",
    "com.google.android.youtube.tvkids|YouTube Kids",
    "com.google.android.play.games|Play Games",
    "com.google.android.feedback|Feedback reporter",
    "com.android.printspooler|Printing service",
    "tv.pluto.android|Pluto TV (preloaded)",
    "com.tubitv|Tubi (preloaded)"
)
$AmbientEntry = "com.google.android.apps.tv.dreamx|Ambient Mode screensaver"

# Never touched: Play Store, Play Services, Chromecast built-in, Google Assistant and voice
# search, the remote service, TV settings, the TV's own input and tuner apps, WebView.

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

# ---------------------------------------------------------------- adb

$Adb = $null
$found = Get-Command adb -ErrorAction SilentlyContinue
if ($found) { $Adb = $found.Path }
$LocalAdb = Join-Path $Here "tools\platform-tools\adb.exe"
if (-not $Adb -and (Test-Path $LocalAdb)) { $Adb = $LocalAdb }
if (-not $Adb) {
    Step "Getting Google's adb tool (one time, about 7 MB)"
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
        $zip = Join-Path $env:TEMP "platform-tools-latest-windows.zip"
        Invoke-WebRequest -UseBasicParsing -Uri "https://dl.google.com/android/repository/platform-tools-latest-windows.zip" -OutFile $zip
        Expand-Archive -Path $zip -DestinationPath (Join-Path $Here "tools") -Force
        Remove-Item $zip -ErrorAction SilentlyContinue
    } catch {
        Fail "Couldn't download adb: $($_.Exception.Message)`n   Download Platform Tools for Windows from developer.android.com/tools/releases/platform-tools,`n   unzip it into the tv\tools folder, and run this again."
    }
    if (-not (Test-Path $LocalAdb)) { Fail "adb.exe wasn't where expected after unzipping: $LocalAdb" }
    $Adb = $LocalAdb
    Ok "Done."
}
if (-not (Test-Path $Apk)) { Fail "Missing $Apk. Download the whole repository again." }

# Runs a command on the TV and returns its output as one string, without Windows line endings.
function TvShell {
    $out = & $Adb -s $Tv shell @args 2>$null | Out-String
    return ($out -replace "`r", "")
}
function TvLines {
    $text = TvShell @args
    return @($text -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" })
}

Write-Host "Sistema Home setup" -ForegroundColor White

# ---------------------------------------------------------------- connect

Step "Connecting to $Tv"
Ok "If the TV asks to allow USB debugging, pick Always allow from this computer, then Allow."
$connected = $false
for ($i = 1; $i -le 12; $i++) {
    & $Adb connect $Tv 2>$null | Out-Null
    $state = (& $Adb -s $Tv get-state 2>$null | Out-String).Trim()
    if ($state -eq "device") { $connected = $true; break }
    if ($i -eq 1) { Ok "Waiting for the TV to accept..." }
    Start-Sleep -Seconds 5
}
if (-not $connected) {
    Fail ("Couldn't connect to $Tv.`n" +
        "   1. On the TV: Settings > System > About, press OK on Android TV OS build 7 times.`n" +
        "   2. Settings > System > Developer options: turn on USB debugging (and Network debugging if listed).`n" +
        "   3. If the TV only offers Wireless debugging with a pairing code, open PowerShell in this folder and run:`n" +
        "        tools\platform-tools\adb.exe pair 192.168.4.40:<pairing port>   (type the code shown on the TV)`n" +
        "      then run this again with -Tv 192.168.4.40:<port shown on the Wireless debugging screen>.`n" +
        "   4. Make sure this computer is on the same Wi-Fi as the TV.")
}
$Model = (TvShell getprop ro.product.model).Trim()
$Maker = (TvShell getprop ro.product.manufacturer).Trim()
$Android = (TvShell getprop ro.build.version.release).Trim()
$Serial = (TvShell getprop ro.serialno).Trim()
if (-not $Serial) { $Serial = "tv" }
Ok "Connected: $Maker $Model, Android $Android"

$Backup = Join-Path $Here "backups\$Serial"
New-Item -ItemType Directory -Force -Path $Backup | Out-Null
$Stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$Enabled = @(TvLines pm list packages -e | ForEach-Object { $_ -replace "^package:", "" } | Sort-Object)
$Disabled = @(TvLines pm list packages -d | ForEach-Object { $_ -replace "^package:", "" } | Sort-Object)
if ($Enabled.Count -eq 0) { Fail "The TV didn't return its package list. Reconnect and try again." }
$Enabled | Set-Content -Path (Join-Path $Backup "enabled-before-$Stamp.txt") -Encoding ASCII
$Disabled | Set-Content -Path (Join-Path $Backup "disabled-before-$Stamp.txt") -Encoding ASCII
$Record = Join-Path $Backup "disabled-by-sistema.txt"
if (-not (Test-Path $Record)) { New-Item -ItemType File -Path $Record | Out-Null }
Ok "Snapshot of every package saved to backups\$Serial\"

function Is-Enabled([string]$p) { return ($Enabled -contains $p) }
function Disable-Pkg([string]$p) {
    $res = TvShell pm disable-user --user 0 $p
    if ($res -notmatch "new state: disabled") { return $false }
    $already = @(Get-Content -Path $Record -ErrorAction SilentlyContinue | Where-Object { $_.Trim() -eq $p })
    if ($already.Count -eq 0) { Add-Content -Path $Record -Value $p -Encoding ASCII }
    return $true
}

# ---------------------------------------------------------------- install

Step "Installing Sistema Home"
$out = & $Adb -s $Tv install -r $Apk 2>&1 | ForEach-Object { "$_" } | Out-String
if ($out -notmatch "Success") {
    if ($out -match "UPDATE_INCOMPATIBLE|signatures do not match") {
        Ok "An older build with a different signature is installed. Replacing it."
        & $Adb -s $Tv uninstall $Pkg 2>$null | Out-Null
        $out = & $Adb -s $Tv install $Apk 2>&1 | ForEach-Object { "$_" } | Out-String
        if ($out -notmatch "Success") { Fail "Install failed: $out" }
    } else {
        Fail "Install failed: $out"
    }
}
Ok "Installed."

# ---------------------------------------------------------------- verify

Step "Making sure it runs before touching anything"
# Single quotes keep multi word values (like a city name) in one piece on the TV side.
TvShell am start -W -n $HomeActivity --es name "'$Name'" --es city "'$City'" --es lat $Lat --es lon $Lon --es units $Units | Out-Null
Start-Sleep -Seconds 4
$appPid = (TvShell pidof $Pkg).Trim()
$activities = TvShell dumpsys activity activities
if (-not $appPid -or ($activities -notmatch ("ResumedActivity.*" + [regex]::Escape($Pkg)))) {
    $crash = & $Adb -s $Tv logcat -d -b crash 2>$null | Select-Object -Last 30 | Out-String
    Write-Host $crash
    Fail "Sistema Home didn't stay open, so nothing else was changed. Your TV is untouched apart from the new app.`n   Send the crash lines above and it gets fixed."
}
Ok "Running on screen. Look at the TV."

# ---------------------------------------------------------------- phone remote

Step "Unlocking the phone remote"
Ok "The TV asks one more time to allow debugging. This time it's Sistema itself,"
Ok "so your iPhone can press buttons inside any app. Tick Always allow, then press Allow."
TvShell dumpsys deviceidle whitelist "+$Pkg" | Out-Null
$TvHost = $Tv.Split(":")[0]
$Remote = "http://${TvHost}:8737"
$remoteState = ""
$reached = $false
for ($i = 1; $i -le 30; $i++) {
    try {
        $health = Invoke-RestMethod -Uri "$Remote/api/health?warm=1" -TimeoutSec 3 -UseBasicParsing
        if ([string]$health.app -eq "sistema-home") { $reached = $true }
        $remoteState = [string]$health.adb
    } catch {
        $remoteState = ""
    }
    if ($remoteState -eq "ok") { break }
    Start-Sleep -Seconds 2
}
$version = ""
$m = [regex]::Match((TvShell dumpsys package $Pkg), "versionName=(\S+)")
if ($m.Success) { $version = $m.Groups[1].Value } else { $version = "unknown" }
$listening = ((TvShell netstat -ltn) -split "`n" | Where-Object { $_ -match ":8737 " }).Count -gt 0
$listenText = "no"
if ($listening) { $listenText = "yes" }
Ok "Sistema version on the TV: $version. Remote server listening on the TV: $listenText"
if ($remoteState -eq "ok") {
    Ok "Phone remote is live and approved. Your phone has full control."
} elseif ($reached) {
    Ok "Phone remote is live. Approval is still pending: the TV asks again the first time you use it."
} elseif ($listening) {
    Ok "The TV is serving the remote, but this computer can't reach it over Wi-Fi."
    Ok "Check that the router isn't isolating devices (guest network or AP isolation)."
} else {
    Ok "The phone remote isn't running on the TV yet. Press Home on the TV, wait 10 seconds and run this again."
}

# ---------------------------------------------------------------- home screen

Step "Setting Sistema as the home screen"
TvShell cmd package set-home-activity $HomeActivity | Out-Null
foreach ($p in $LauncherPkgs) {
    if (Is-Enabled $p) {
        if (Disable-Pkg $p) { Ok "Off: $p" }
        else { Ok "Couldn't disable $p (the TV blocked it). Press Home and pick Sistema if asked." }
    }
}
TvShell cmd package set-home-activity $HomeActivity | Out-Null

# ---------------------------------------------------------------- debloat

Step "Debloat"
$list = @($Bloat)
if ($Ambient) { $list += $AmbientEntry }
$targets = @()
foreach ($entry in $list) {
    $parts = $entry.Split("|")
    if (Is-Enabled $parts[0]) {
        Write-Host ("   {0,-42} {1}" -f $parts[0], $parts[1])
        $targets += $parts[0]
    }
}
if ($targets.Count -eq 0) {
    Ok "Nothing to remove. Already clean."
} else {
    $go = $true
    if (-not $Yes) {
        $reply = Read-Host "   Turn these off? [Y/n]"
        if ($reply -match "^(n|no)$") { $go = $false }
    }
    if ($go) {
        foreach ($p in $targets) {
            if (Disable-Pkg $p) { Ok "Off: $p" } else { Ok "Skipped: $p (protected on this TV)" }
        }
    } else {
        Ok "Left as is."
    }
}

# ---------------------------------------------------------------- summary

Step "Your apps"
$apps = @(
    "YouTube|com.google.android.youtube.tv",
    "Spotify|com.spotify.tv.android",
    "Paramount+|com.cbs.ott",
    "Netflix|com.netflix.ninja",
    "HBO Max|com.wbd.stream",
    "YouTube TV|com.google.android.youtube.tvunplugged"
)
foreach ($a in $apps) {
    $parts = $a.Split("|")
    if (Is-Enabled $parts[1]) { Ok "$($parts[0])  ready" }
    else { Ok "$($parts[0])  not installed yet. Its tile opens Google Play." }
}

TvShell input keyevent KEYCODE_HOME | Out-Null

Step "Your iPhone remote"
Ok "On your iPhone, on the same Wi-Fi, open Safari and go to:"
Ok ""
Write-Host "       $Remote" -ForegroundColor White
Ok ""
Ok "Tap Share, then Add to Home Screen. Open it and type the code the TV shows."

Step "Done"
Ok "Press Home on the remote. That's your screen now."
Ok "Hold OK on any app (or press Menu) to move it, remove it, or pin it."
Ok "Undo everything any time: double-click Restore-Windows.cmd"
