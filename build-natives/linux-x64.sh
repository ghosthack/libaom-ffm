#!/bin/sh
# Build and stage libaom for linux-x64.
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${1:-$ROOT/build/aom-3.14.1}"
PREFIX="$ROOT/build/prefix-linux-x64"
BUILD="$ROOT/build/aom-build-linux-x64"
DEST="$ROOT/natives/src/main/resources/natives/linux-x64"

[ -f "$SRC/CMakeLists.txt" ] || { echo "libaom source not found at $SRC"; exit 1; }
cmake -S "$SRC" -B "$BUILD" -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX="$PREFIX" \
  -DCMAKE_INSTALL_RPATH='$ORIGIN' \
  -DBUILD_SHARED_LIBS=ON \
  -DENABLE_DOCS=OFF -DENABLE_EXAMPLES=OFF -DENABLE_TESTS=OFF -DENABLE_TOOLS=OFF
cmake --build "$BUILD" --parallel "$(nproc)"
cmake --install "$BUILD"

rm -rf "$DEST"
mkdir -p "$DEST"
SO="$(readlink -f "$PREFIX/lib/libaom.so")"
NAME="$(patchelf --print-soname "$SO")"
cp "$SO" "$DEST/$NAME"
chmod 644 "$DEST/$NAME"
patchelf --set-rpath '$ORIGIN' "$DEST/$NAME"
echo "$NAME" > "$DEST/manifest.txt"

if readelf -d "$DEST/$NAME" | awk '/NEEDED/ {print $NF}' | tr -d '[]' \
    | grep -vE '^(libc|libm|libpthread|libdl|librt)\.so|^ld-linux'; then
  echo "ERROR: staged libaom has non-system dependencies (above)"; exit 1
fi
cp "$SRC/LICENSE" "$DEST/LICENSE.libaom"
cp "$SRC/PATENTS" "$DEST/PATENTS.libaom"
{
  echo "libaom 3.14.1 (linux-x64), built from unmodified source"
  echo "Source: https://aomedia.googlesource.com/aom/+/refs/tags/v3.14.1"
  echo "License: BSD-2-Clause and Alliance for Open Media Patent License 1.0"
  echo "CMake: BUILD_SHARED_LIBS=ON, tools/examples/tests/docs disabled"
} > "$DEST/BUILD-INFO.txt"
echo "Staged libaom into $DEST"

