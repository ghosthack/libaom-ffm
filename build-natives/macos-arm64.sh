#!/bin/sh
# Build and stage libaom for macos-arm64.
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${1:-$ROOT/build/aom-3.14.1}"
PREFIX="$ROOT/build/prefix-macos-arm64"
BUILD="$ROOT/build/aom-build-macos-arm64"
DEST="$ROOT/natives/src/main/resources/natives/macos-arm64"

[ -f "$SRC/CMakeLists.txt" ] || { echo "libaom source not found at $SRC"; exit 1; }

cmake -S "$SRC" -B "$BUILD" -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX="$PREFIX" \
  -DCMAKE_INSTALL_NAME_DIR='@loader_path' \
  -DBUILD_SHARED_LIBS=ON \
  -DENABLE_DOCS=OFF -DENABLE_EXAMPLES=OFF -DENABLE_TESTS=OFF -DENABLE_TOOLS=OFF
cmake --build "$BUILD" --parallel "$(sysctl -n hw.ncpu)"
cmake --install "$BUILD"

rm -rf "$DEST"
mkdir -p "$DEST"
DYLIB="$(readlink -f "$PREFIX/lib/libaom.dylib")"
NAME="$(basename "$(otool -D "$DYLIB" | tail -1)")"
cp "$DYLIB" "$DEST/$NAME"
chmod 644 "$DEST/$NAME"
echo "$NAME" > "$DEST/manifest.txt"

if otool -L "$DEST/$NAME" | tail -n +2 | grep -vE '@loader_path/|/usr/lib/|/System/Library/'; then
  echo "ERROR: staged libaom has non-relocatable dependencies (above)"; exit 1
fi
cp "$SRC/LICENSE" "$DEST/LICENSE.libaom"
cp "$SRC/PATENTS" "$DEST/PATENTS.libaom"
{
  echo "libaom 3.14.1 (macos-arm64), built from unmodified source"
  echo "Source: https://aomedia.googlesource.com/aom/+/refs/tags/v3.14.1"
  echo "License: BSD-2-Clause and Alliance for Open Media Patent License 1.0"
  echo "CMake: BUILD_SHARED_LIBS=ON, tools/examples/tests/docs disabled"
} > "$DEST/BUILD-INFO.txt"
echo "Staged libaom into $DEST"

