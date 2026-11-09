#!/usr/bin/env bash
# Sistema Home: install the launcher, make it the home screen, and strip the bloat.
#
#   ./setup.sh                    # TV at 192.168.4.40
#   ./setup.sh 192.168.4.40:5555  # explicit address
#   ./setup.sh --yes              # no questions
#   ./setup.sh --ambient          # also turn off Google's Ambient Mode screensaver
#
# Everything is disabled, never deleted. ./restore.sh puts it all back.
set -euo pipefail

ADDR="192.168.4.40"
ASSUME_YES=0
KILL_AMBIENT=0
for arg in "$@"; do
  case "$arg" in
    -y|--yes) ASSUME_YES=1 ;;
    --ambient) KILL_AMBIENT=1 ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) ADDR="$arg" ;;
  esac
done
case "$ADDR" in *:*) ;; *) ADDR="$ADDR:5555" ;; esac

HERE="$(cd "$(dirname "$0")" && pwd)"
APK="$HERE/dist/sistema-home.apk"
PKG="com.sistema.home"
HOME_ACTIVITY="$PKG/.HomeActivity"

# Name, city and coordinates for the greeting and the weather. Override like:
#   NAME=Nicolas CITY=Brickell LAT=25.7617 LON=-80.1918 ./setup.sh
NAME="${NAME:-Nicolas}"
CITY="${CITY:-Miami}"
LAT="${LAT:-25.7617}"
LON="${LON:--80.1918}"
UNITS="${UNITS:-f}"

# The stock Google TV home and the setup app that grabs Home when the launcher is off.
LAUNCHER_PKGS="com.google.android.apps.tv.launcherx com.google.android.tungsten.setupwraith"

# Ads, recommendation feeds and preloaded apps you don't use. Only touched if present.
BLOAT_PKGS="
com.google.android.tvrecommendations|Sponsored rows and recommendation feed
com.google.android.videos|Google TV movie store
com.google.android.youtube.tvmusic|YouTube Music (you're on Spotify)
com.google.android.youtube.tvkids|YouTube Kids
com.google.android.play.games|Play Games
com.google.android.feedback|Feedback reporter
com.android.printspooler|Printing service
tv.pluto.android|Pluto TV (preloaded)
com.tubitv|Tubi (preloaded)
"
AMBIENT_PKG="com.google.android.apps.tv.dreamx|Ambient Mode screensaver"

# Never touched. Listed so you know they're safe:
#   Play Store, Play Services, Chromecast built-in, Google Assistant and voice search,
#   remote service, TV settings, the TV's own input and tuner apps, WebView.

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
step() { printf '\n\033[38;5;173m%s\033[0m %s\n' "::" "$*"; }
ok() { printf '   %s\n' "$*"; }
die() { printf '\n\033[31m%s\033[0m\n' "$*" >&2; exit 1; }
ask() {
  [ "$ASSUME_YES" = 1 ] && return 0
  printf '   %s [Y/n] ' "$1"
  read -r reply </dev/tty || reply=""
  case "$reply" in n|N|no|No) return 1 ;; *) return 0 ;; esac
}

command -v adb >/dev/null 2>&1 || die "adb isn't installed.
   Mac:    brew install android-platform-tools
   Linux:  sudo apt install adb
   Or download Platform Tools from developer.android.com/tools/releases/platform-tools"
[ -f "$APK" ] || die "Missing $APK. Run tools/build-apk.sh or grab the APK from GitHub Actions."

sh_tv() { adb -s "$ADDR" shell "$@" 2>/dev/null | tr -d '\r' || true; }
# Output is captured before grepping: with pipefail, "cmd | grep -q" can report a false failure.
disable_pkg() {
  local res
  res="$(sh_tv pm disable-user --user 0 "$1")"
  case "$res" in *"new state: disabled"*) ;; *) return 1 ;; esac
  grep -qx "$1" "$BACKUP/disabled-by-sistema.txt" || echo "$1" >> "$BACKUP/disabled-by-sistema.txt"
}

bold "Sistema Home setup"

step "Connecting to $ADDR"
ok "If the TV asks to allow USB debugging, pick Always allow from this computer, then Allow."
connected=0
for attempt in 1 2 3 4 5 6 7 8 9 10 11 12; do
  adb connect "$ADDR" >/dev/null 2>&1 || true
  state="$(adb -s "$ADDR" get-state 2>/dev/null || true)"
  if [ "$state" = "device" ]; then connected=1; break; fi
  [ "$attempt" = 1 ] && ok "Waiting for the TV to accept..."
  sleep 5
done
if [ "$connected" != 1 ]; then
  die "Couldn't connect to $ADDR.
   1. On the TV: Settings > System > About, press OK on Android TV OS build 7 times.
   2. Settings > System > Developer options: turn on USB debugging (and Network debugging if listed).
   3. If the TV only offers Wireless debugging with a pairing code:
        adb pair 192.168.4.40:<pairing port>     (enter the code shown on the TV)
        ./setup.sh 192.168.4.40:<port shown on the Wireless debugging screen>
   4. Make sure this computer is on the same Wi-Fi as the TV."
fi
MODEL="$(sh_tv getprop ro.product.model)"
MAKER="$(sh_tv getprop ro.product.manufacturer)"
ANDROID="$(sh_tv getprop ro.build.version.release)"
SERIAL="$(sh_tv getprop ro.serialno)"
ok "Connected: $MAKER $MODEL, Android $ANDROID"

BACKUP="$HERE/backups/${SERIAL:-tv}"
mkdir -p "$BACKUP"
STAMP="$(date +%Y%m%d-%H%M%S)"
pkgs_on="$(sh_tv pm list packages -e)"
pkgs_off="$(sh_tv pm list packages -d)"
printf '%s\n' "$pkgs_on" | sed 's/^package://' | sort > "$BACKUP/enabled-before-$STAMP.txt"
printf '%s\n' "$pkgs_off" | sed 's/^package://' | sort > "$BACKUP/disabled-before-$STAMP.txt"
[ -s "$BACKUP/enabled-before-$STAMP.txt" ] || die "The TV didn't return its package list. Reconnect and try again."
touch "$BACKUP/disabled-by-sistema.txt"
ok "Snapshot of every package saved to backups/${SERIAL:-tv}/"
ENABLED="$BACKUP/enabled-before-$STAMP.txt"
is_enabled() { grep -qx "$1" "$ENABLED"; }

step "Installing Sistema Home"
if ! out="$(adb -s "$ADDR" install -r "$APK" 2>&1)"; then
  if grep -qE "UPDATE_INCOMPATIBLE|signatures do not match" <<<"$out"; then
    ok "An older build with a different signature is installed. Replacing it."
    adb -s "$ADDR" uninstall "$PKG" >/dev/null 2>&1 || true
    adb -s "$ADDR" install "$APK" >/dev/null || die "Install failed: $out"
  else
    die "Install failed: $out"
  fi
fi
ok "Installed."

step "Making sure it runs before touching anything"
# Single quotes keep multi word values (like a city name) in one piece on the TV side.
sh_tv am start -W -n "$HOME_ACTIVITY" \
  --es name "'$NAME'" --es city "'$CITY'" --es lat "$LAT" --es lon "$LON" --es units "$UNITS" >/dev/null
sleep 4
activities="$(sh_tv dumpsys activity activities)"
if [ -z "$(sh_tv pidof "$PKG")" ] || ! grep -qE "ResumedActivity.*$PKG" <<<"$activities"; then
  crash="$(adb -s "$ADDR" logcat -d -b crash 2>/dev/null || true)"
  printf '%s\n' "$crash" | tail -30
  die "Sistema Home didn't stay open, so nothing else was changed. Your TV is untouched apart from the new app.
   Send the crash lines above and it gets fixed."
fi
ok "Running on screen. Look at the TV."

step "Unlocking the phone remote"
ok "The TV asks one more time to allow debugging. This time it's Sistema itself,"
ok "so your iPhone can press buttons inside any app. Tick Always allow, then press Allow."
sh_tv dumpsys deviceidle whitelist +"$PKG" >/dev/null
REMOTE="http://${ADDR%%:*}:8737"
remote_state=""
if command -v curl >/dev/null 2>&1; then
  for _ in $(seq 1 "${REMOTE_WAIT:-30}"); do
    health="$(curl -s -m 3 "$REMOTE/api/health?warm=1" 2>/dev/null || true)"
    remote_state="$(sed -n 's/.*"adb":"\([a-z]*\)".*/\1/p' <<<"$health")"
    [ "$remote_state" = "ok" ] && break
    sleep 2
  done
fi
if [ "$remote_state" = "ok" ]; then
  ok "Approved. Your phone has full control."
else
  ok "Not approved yet. No problem: the TV asks again the first time you use the phone remote."
fi

step "Setting Sistema as the home screen"
sh_tv cmd package set-home-activity "$HOME_ACTIVITY" >/dev/null || true
for pkg in $LAUNCHER_PKGS; do
  if is_enabled "$pkg"; then
    if disable_pkg "$pkg"; then
      ok "Off: $pkg"
    else
      ok "Couldn't disable $pkg (the TV blocked it). Press Home and pick Sistema if asked."
    fi
  fi
done
sh_tv cmd package set-home-activity "$HOME_ACTIVITY" >/dev/null || true

step "Debloat"
list="$BLOAT_PKGS"
[ "$KILL_AMBIENT" = 1 ] && list="$list
$AMBIENT_PKG"
targets=""
while IFS='|' read -r pkg why; do
  [ -n "$pkg" ] || continue
  if is_enabled "$pkg"; then
    printf '   %-42s %s\n' "$pkg" "$why"
    targets="$targets $pkg"
  fi
done <<EOF
$list
EOF
if [ -z "$targets" ]; then
  ok "Nothing to remove. Already clean."
elif ask "Turn these off?"; then
  for pkg in $targets; do
    if disable_pkg "$pkg"; then
      ok "Off: $pkg"
    else
      ok "Skipped: $pkg (protected on this TV)"
    fi
  done
else
  ok "Left as is."
fi

step "Your apps"
for slot in "YouTube:com.google.android.youtube.tv" "Spotify:com.spotify.tv.android" \
            "Paramount+:com.cbs.ott" "Netflix:com.netflix.ninja" "HBO Max:com.wbd.stream" \
            "YouTube TV:com.google.android.youtube.tvunplugged"; do
  label="${slot%%:*}"; pkg="${slot#*:}"
  if is_enabled "$pkg"; then ok "$label  ready"; else ok "$label  not installed yet. Its tile opens Google Play."; fi
done

sh_tv input keyevent KEYCODE_HOME >/dev/null || true

step "Your iPhone remote"
ok "On your iPhone, on the same Wi-Fi, open Safari and go to:"
ok ""
ok "    $REMOTE"
ok ""
ok "Tap Share, then Add to Home Screen. Open it and type the code the TV shows."

step "Done"
ok "Press Home on the remote. That's your screen now."
ok "Hold OK on any app (or press Menu) to move it, remove it, or pin it."
ok "Undo everything any time:  ./restore.sh $ADDR"
