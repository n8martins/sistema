#!/usr/bin/env bash
# Puts the TV back exactly how Google shipped it.
#
#   ./restore.sh                     # TV at 192.168.4.40, removes Sistema Home
#   ./restore.sh 192.168.4.40:5555
#   ./restore.sh --keep-app          # bring Google TV home back but keep Sistema installed
set -euo pipefail

ADDR="192.168.4.40"
KEEP_APP=0
for arg in "$@"; do
  case "$arg" in
    --keep-app) KEEP_APP=1 ;;
    -h|--help) sed -n '2,7p' "$0"; exit 0 ;;
    *) ADDR="$arg" ;;
  esac
done
case "$ADDR" in *:*) ;; *) ADDR="$ADDR:5555" ;; esac

HERE="$(cd "$(dirname "$0")" && pwd)"
PKG="com.sistema.home"
# Always restored, even if the backup folder is gone.
ALWAYS="com.google.android.apps.tv.launcherx com.google.android.tungsten.setupwraith"
FALLBACK="com.google.android.tvrecommendations com.google.android.videos com.google.android.youtube.tvmusic
com.google.android.youtube.tvkids com.google.android.play.games com.google.android.feedback
com.android.printspooler tv.pluto.android com.tubitv com.google.android.apps.tv.dreamx"

step() { printf '\n\033[38;5;173m%s\033[0m %s\n' "::" "$*"; }
ok() { printf '   %s\n' "$*"; }
die() { printf '\n\033[31m%s\033[0m\n' "$*" >&2; exit 1; }
sh_tv() { adb -s "$ADDR" shell "$@" 2>/dev/null | tr -d '\r' || true; }

command -v adb >/dev/null 2>&1 || die "adb isn't installed. Mac: brew install android-platform-tools"

step "Connecting to $ADDR"
for _ in 1 2 3 4 5 6 7 8 9 10 11 12; do
  adb connect "$ADDR" >/dev/null 2>&1 || true
  [ "$(adb -s "$ADDR" get-state 2>/dev/null || true)" = "device" ] && break
  sleep 5
done
[ "$(adb -s "$ADDR" get-state 2>/dev/null || true)" = "device" ] || die "Couldn't connect to $ADDR. Check Developer options on the TV."
SERIAL="$(sh_tv getprop ro.serialno)"
RECORD="$HERE/backups/${SERIAL:-tv}/disabled-by-sistema.txt"

if [ -s "$RECORD" ]; then
  ok "Using the record from setup: backups/${SERIAL:-tv}/"
  pkgs="$ALWAYS $(tr '\n' ' ' < "$RECORD")"
else
  ok "No setup record found for this TV. Re-enabling the standard list."
  pkgs="$ALWAYS $FALLBACK"
fi

step "Turning everything back on"
seen=" "
for pkg in $pkgs; do
  case "$seen" in *" $pkg "*) continue ;; esac
  seen="$seen$pkg "
  res="$(sh_tv pm enable --user 0 "$pkg")"
  case "$res" in
    *"new state: enabled"*) ok "On: $pkg" ;;
    *) ok "Not on this TV: $pkg" ;;
  esac
done

if [ "$KEEP_APP" = 1 ]; then
  step "Keeping Sistema Home installed"
  ok "Press Home. If the TV asks which home screen to use, pick Google TV Home."
else
  step "Removing Sistema Home"
  if adb -s "$ADDR" uninstall "$PKG" >/dev/null 2>&1; then ok "Removed."; else ok "It wasn't installed."; fi
fi

if [ -s "$RECORD" ]; then mv "$RECORD" "$RECORD.restored-$(date +%Y%m%d-%H%M%S)"; fi
sh_tv input keyevent KEYCODE_HOME >/dev/null

step "Done. Your TV is back to stock."
