#!/usr/bin/env bash
# Prepare tout ce que le banc OCR embarque dans son APK de test, et que le depot ne
# versionne pas : les images, les modeles du moteur Rust et la bibliotheque Rust.
#
# Usage : ocrbench/prepare.sh [abi...]      (defaut : x86_64, l'emulateur)
#   ANDROID_NDK_HOME (ou ANDROID_NDK_LATEST_HOME) doit designer un NDK.
#   PYTHON peut designer un Python disposant de Pillow.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$HERE/build"
ASSETS="$OUT/assets/ocrbench"
PYTHON="${PYTHON:-python3}"
ABIS=("$@")
[ ${#ABIS[@]} -eq 0 ] && ABIS=(x86_64)

# --- Images : generees, jamais versionnees (une vingtaine de Mo).
"$PYTHON" "$HERE/generate_samples.py" "$ASSETS"

# --- Modeles d'ocrs, publies par son auteur. L'empreinte est verifiee : un modele
# different donnerait d'autres mesures sans que rien ne le signale.
mkdir -p "$ASSETS/models"
fetch() {
    local url="$1" file="$2" sha="$3"
    if [ ! -f "$file" ] || ! echo "$sha  $file" | sha256sum --check --status; then
        curl --fail --silent --show-error --location --retry 3 -o "$file" "$url"
    fi
    echo "$sha  $file" | sha256sum --check --quiet
}
HF="https://huggingface.co/robertknight/ocrs/resolve/main"
fetch "$HF/text-detection-ssfbcj81.onnx" "$ASSETS/models/text-detection.onnx" \
    c15df092060abe9417929b04eaf552b98946e15144036b3c83b09a3bf4d5d5a7
fetch "$HF/text-rec-checkpoint-s52qdbqt.onnx" "$ASSETS/models/text-recognition.onnx" \
    3774cdfe8ba082a17c181d5ab0698f3ff2fd0def07cd2b9134faec9b1a191919

# --- Bibliotheque Rust, une par ABI. Aucune dependance C : l'editeur de liens du NDK
# suffit, sans cargo-ndk.
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_LATEST_HOME:-}}"
if [ -z "$NDK" ] || [ ! -d "$NDK" ]; then
    echo "NDK introuvable : definir ANDROID_NDK_HOME" >&2
    exit 1
fi
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
API=26  # minSdk de l'application
for abi in "${ABIS[@]}"; do
    case "$abi" in
        x86_64) triple=x86_64-linux-android ;;
        arm64-v8a) triple=aarch64-linux-android ;;
        *) echo "ABI inconnue : $abi" >&2; exit 1 ;;
    esac
    var="$(echo "$triple" | tr 'a-z-' 'A-Z_')"
    export "CARGO_TARGET_${var}_LINKER=$TOOLCHAIN/${triple}${API}-clang"
    rustup target add "$triple" > /dev/null
    (cd "$HERE/rust" && cargo build --release --lib --locked --target "$triple")
    mkdir -p "$OUT/jniLibs/$abi"
    cp "$HERE/rust/target/$triple/release/libocrbench.so" "$OUT/jniLibs/$abi/"
done

echo "--- Poids de ce que le moteur Rust ajouterait a l'application"
ls -l "$OUT"/jniLibs/*/libocrbench.so "$ASSETS"/models/*.onnx | awk '{printf "%9.2f Mo  %s\n", $5 / 1048576, $9}'
