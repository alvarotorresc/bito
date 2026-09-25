#!/usr/bin/env bash
# Emulador headless para las capturas de Bito.
# Uso: emulator.sh setup | up | down | locale es|en | demo
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"
export JAVA_HOME="$HOME/.jdks/jdk-21.0.12+8"
ADB="$SDK/platform-tools/adb"
EMU="$SDK/emulator/emulator"
SDKM="$SDK/cmdline-tools/latest/bin/sdkmanager"
AVDM="$SDK/cmdline-tools/latest/bin/avdmanager"
HERE="$(cd "$(dirname "$0")" && pwd)"
AVD=bito-shots
IMG="system-images;android-35;default;x86_64"
SERIAL=emulator-5554
CONFIG="$HOME/.android/avd/$AVD.avd/config.ini"
TARGET_H=2340
mkdir -p "$HERE/raw"

a() { "$ADB" -s "$SERIAL" "$@"; }

set_kv() {
  local key="$1" value="$2"
  if grep -q "^$key=" "$CONFIG"; then
    sed -i "s|^$key=.*|$key=$value|" "$CONFIG"
  else
    echo "$key=$value" >> "$CONFIG"
  fi
}

wait_boot() {
  if ! timeout 120 "$ADB" -s "$SERIAL" wait-for-device; then
    echo "emulator.sh: adb no vio el emulador en 120 s (mira $HERE/raw/emulator.log)" >&2
    exit 1
  fi
  for _ in $(seq 1 150); do
    if [ "$(a shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
      return 0
    fi
    sleep 2
  done
  echo "emulator.sh: el arranque no terminó en 300 s (mira $HERE/raw/emulator.log)" >&2
  exit 1
}

up() {
  if a get-state >/dev/null 2>&1; then
    echo "emulator.sh: ya hay un emulador en $SERIAL"
    return 0
  fi
  nohup "$EMU" -avd "$AVD" -no-window -gpu swiftshader_indirect -no-audio -no-boot-anim \
    -no-snapshot -timezone Europe/Madrid "$@" > "$HERE/raw/emulator.log" 2>&1 &
  wait_boot
  a shell settings put global window_animation_scale 0
  a shell settings put global transition_animation_scale 0
  a shell settings put global animator_duration_scale 0
  a shell cmd overlay enable com.android.internal.systemui.navbar.gestural
  sleep 5
}

down() {
  a emu kill >/dev/null 2>&1 || true
  for _ in $(seq 1 30); do
    a get-state >/dev/null 2>&1 || break
    sleep 1
  done
  for _ in $(seq 1 30); do
    pgrep -f -- "-avd $AVD( |$)" >/dev/null || return 0
    sleep 1
  done
  echo "emulator.sh: el emulador no se apagó" >&2
  exit 1
}

# Imprime "TOP NAV HEIGHT" en píxeles a partir de las fuentes de insets de dumpsys window.
measure() {
  local dump top nav_top h
  dump="$(a shell dumpsys window | tr -d '\r')"
  top="$(grep -oE 'type=statusBars frame=\[0,0\]\[1080,[0-9]+\]' <<<"$dump" | head -1 | sed -E 's/.*\[1080,([0-9]+)\]$/\1/')"
  nav_top="$(grep -oE 'type=navigationBars frame=\[0,[0-9]+\]\[1080,[0-9]+\]' <<<"$dump" | head -1 | sed -E 's/.*frame=\[0,([0-9]+)\].*/\1/')"
  h="$(a shell wm size | tr -d '\r' | grep Physical | grep -oE '[0-9]+$')"
  if [ -z "$top" ] || [ -z "$nav_top" ] || [ -z "$h" ]; then
    echo "emulator.sh: no encuentro statusBars/navigationBars en dumpsys window" >&2
    exit 1
  fi
  echo "$top $((h - nav_top)) $h"
}

setup() {
  yes | "$SDKM" --licenses >/dev/null || true
  "$SDKM" --install "emulator" "platform-tools" "$IMG"
  if ! "$EMU" -list-avds | grep -qx "$AVD"; then
    echo no | "$AVDM" create avd -n "$AVD" -k "$IMG" --force
  fi
  set_kv hw.lcd.width 1080
  set_kv hw.lcd.height "$TARGET_H"
  set_kv hw.lcd.density 440
  set_kv hw.mainKeys no
  set_kv hw.keyboard yes
  set_kv hw.ramSize 4096
  set_kv disk.dataPartition.size 6G
  set_kv hw.gpu.enabled yes
  set_kv hw.gpu.mode swiftshader_indirect
  down
  up
  local top nav h
  # Solo se recorta la barra de estado: la app es edge-to-edge y su cápsula de navegación
  # llega bajo la zona de gestos, así que esa franja (con el asa) se queda en la captura.
  read -r top nav h <<<"$(measure)"
  down
  set_kv hw.lcd.height "$((TARGET_H + top))"
  up
  local top2 nav2 h2
  read -r top2 nav2 h2 <<<"$(measure)"
  if [ "$top2" != "$top" ] || [ "$nav2" != "$nav" ] || [ "$h2" != "$((TARGET_H + top))" ]; then
    echo "emulator.sh: las barras cambiaron al crecer la pantalla ($top/$nav -> $top2/$nav2, alto $h2)" >&2
    exit 1
  fi
  printf 'TOP=%s\nBOTTOM=0\nHEIGHT=%s\n' "$top2" "$h2" > "$HERE/avd.env"
  cat "$HERE/avd.env"
}

current_locale_is() {
  a shell am get-config 2>/dev/null | tr -d '\r' | grep -qE "(^|[ -])$1-"
}

wait_config() {
  for _ in $(seq 1 60); do
    if current_locale_is "$1" && a shell pidof com.android.systemui >/dev/null 2>&1; then
      sleep 5
      return 0
    fi
    sleep 2
  done
  return 1
}

demo() {
  a shell settings put global sysui_demo_allowed 1
  d() { a shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
  d enter
  d clock -e hhmm 0941
  d battery -e level 100 -e plugged false
  d network -e wifi show -e level 4 -e fully true
  d network -e mobile show -e datatype none -e level 4 -e fully true
  d notifications -e visible false
}

locale() {
  local tag want
  case "${1:-}" in
    es) tag=es-ES ;;
    en) tag=en-US ;;
    *) echo "uso: emulator.sh locale es|en" >&2; exit 2 ;;
  esac
  want="${tag/-/-r}"
  up
  if current_locale_is "$want"; then
    echo "locale: $tag ya activo"
    demo
    return 0
  fi
  if a root 2>&1 | grep -qiE 'restarting|already running as root'; then
    a wait-for-device
    a shell setprop persist.sys.locale "$tag"
    a shell stop
    a shell start
    if wait_config "$want"; then
      echo "locale: $tag por setprop y reinicio del framework"
      demo
      return 0
    fi
  fi
  down
  up -change-locale "$tag"
  if wait_config "$want"; then
    echo "locale: $tag por -change-locale"
    demo
    return 0
  fi
  echo "emulator.sh: no consigo poner $tag" >&2
  exit 1
}

case "${1:-}" in
  setup) setup ;;
  up) up ;;
  down) down ;;
  locale) locale "${2:-}" ;;
  demo) demo ;;
  *) echo "uso: emulator.sh setup|up|down|locale es|en|demo" >&2; exit 2 ;;
esac
