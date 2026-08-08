#!/bin/sh
# Build and stage libaom for windows-x64 from an MSYS2 MINGW64 shell.
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${1:-$ROOT/build/aom-3.14.1}"
PREFIX="$ROOT/build/prefix-windows-x64"
BUILD="$ROOT/build/aom-build-windows-x64"
DEST="$ROOT/natives/src/main/resources/natives/windows-x64"

[ -f "$SRC/CMakeLists.txt" ] || { echo "libaom source not found at $SRC"; exit 1; }
cmake -S "$SRC" -B "$BUILD" -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX="$PREFIX" \
  -DBUILD_SHARED_LIBS=ON \
  -DENABLE_DOCS=OFF -DENABLE_EXAMPLES=OFF -DENABLE_TESTS=OFF -DENABLE_TOOLS=OFF
cmake --build "$BUILD" --parallel "$(nproc)"
cmake --install "$BUILD"

rm -rf "$DEST"
mkdir -p "$DEST"
: > "$DEST/manifest.txt"
for runtime in libwinpthread-1.dll libgcc_s_seh-1.dll; do
  if [ -f "/mingw64/bin/$runtime" ]; then
    cp "/mingw64/bin/$runtime" "$DEST/$runtime"
    echo "$runtime" >> "$DEST/manifest.txt"
  fi
done
DLL="$(find "$PREFIX/bin" "$PREFIX/lib" -maxdepth 1 -type f \
  \( -iname 'aom*.dll' -o -iname 'libaom*.dll' \) ! -iname '*av1_rc*' | head -1)"
[ -n "$DLL" ] || { echo "built libaom DLL not found"; exit 1; }
NAME="$(basename "$DLL")"
cp "$DLL" "$DEST/$NAME"
echo "$NAME" >> "$DEST/manifest.txt"

ALLOWED='aom|libaom|libwinpthread|libgcc_s_seh|KERNEL32|USER32|ADVAPI32|msvcrt|ntdll|api-ms-'
for file in "$DEST"/*.dll; do
  if objdump -p "$file" | awk '/DLL Name:/{print $3}' | grep -viE "^($ALLOWED)"; then
    echo "ERROR: $file imports unexpected DLLs (above)"; exit 1
  fi
done
cp "$SRC/LICENSE" "$DEST/LICENSE.libaom"
cp "$SRC/PATENTS" "$DEST/PATENTS.libaom"
{
  echo "libaom 3.14.1 (windows-x64), built from unmodified source with MSYS2 mingw-w64"
  echo "Source: https://aomedia.googlesource.com/aom/+/refs/tags/v3.14.1"
  echo "License: BSD-2-Clause and Alliance for Open Media Patent License 1.0"
  echo "CMake: BUILD_SHARED_LIBS=ON, tools/examples/tests/docs disabled"
} > "$DEST/BUILD-INFO.txt"
echo "Staged libaom into $DEST"
