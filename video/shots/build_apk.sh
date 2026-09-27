#!/usr/bin/env bash
# Build debug de Bito para las capturas. No toca el código de la app.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export JAVA_HOME="$HOME/.jdks/jdk-21.0.12+8"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
AAPT="$ANDROID_HOME/build-tools/35.0.0/aapt2"
(cd "$ROOT" && ./gradlew --quiet assembleDebug)
BADGING="$("$AAPT" dump badging "$APK")"
grep -q "package: name='com.alvarotc.bito'" <<<"$BADGING" || { echo "build_apk.sh: paquete inesperado" >&2; exit 1; }
grep -q "versionName='1.3.0'" <<<"$BADGING" || { echo "build_apk.sh: versionName no es 1.3.0" >&2; exit 1; }
grep -q "application-debuggable" <<<"$BADGING" || { echo "build_apk.sh: la APK no es depurable, run-as no funcionará" >&2; exit 1; }
echo "build_apk.sh: $APK (com.alvarotc.bito 1.3.0, depurable)"
