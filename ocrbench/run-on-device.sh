#!/usr/bin/env bash
# Lance le banc OCR sur l'appareil ou l'emulateur que voit adb, et en extrait le rapport.
# Les deux APK doivent avoir ete compiles avec ocrbench/bench.init.gradle (voir README.md).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$HERE")"
RESULTS="$HERE/build/results"
APP=com.suiviemat.app
mkdir -p "$RESULTS"

adb install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
adb install -r -t "$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

# Compilation complete a l'avance, comme une application installee depuis longtemps :
# sans elle, le Kotlin serait mesure en partie interprete, le temps que le JIT le
# rattrape, et le resultat dependrait de la duree de l'echauffement.
adb shell cmd package compile -m speed -f "$APP" || echo "compilation a l'avance refusee pour $APP" >&2
adb shell cmd package compile -m speed -f "$APP.test" || echo "compilation a l'avance refusee pour $APP.test" >&2

adb logcat -c || true
adb shell am instrument -w -e class com.scanlist.app.ocrbench.OcrBenchmark \
    "$APP.test/androidx.test.runner.AndroidJUnitRunner" | tee "$RESULTS/instrument.txt"

sed -n '/=== BANC OCR : DEBUT ===/,/=== BANC OCR : FIN ===/p' "$RESULTS/instrument.txt" \
    | sed '1d;$d' > "$RESULTS/rapport.md"

# `am instrument` rend 0 meme quand le test echoue ou que le processus tombe.
if ! grep -q '=== BANC OCR : FIN ===' "$RESULTS/instrument.txt" || ! grep -q '^OK (' "$RESULTS/instrument.txt"; then
    echo "Le banc ne s'est pas termine normalement. Fin du journal de l'appareil :" >&2
    adb logcat -d -t 400 '*:W' >&2 || true
    exit 1
fi
