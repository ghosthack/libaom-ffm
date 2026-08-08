#!/bin/sh
# Generate the curated libaom FFM surface and route symbol lookup through the
# runtime loader. JDK 22 jextract is intentionally used to match the Java floor.
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JX="${JEXTRACT:-$ROOT/tools/jextract-22/bin/jextract}"
PREFIX="${AOM_PREFIX:-$ROOT/build/prefix-macos-arm64}"
OUT="$ROOT/core/src/main/java"
PKG="io.github.ghosthack.libaomffm.aom"
SDK="$(xcrun --show-sdk-path)"

[ -x "$JX" ] || { echo "jextract not found at $JX (or set JEXTRACT)"; exit 1; }
[ -d "$PREFIX/include/aom" ] || { echo "libaom headers not found below $PREFIX (or set AOM_PREFIX)"; exit 1; }

GEN_DIR="$OUT/$(echo "$PKG" | tr . /)"
rm -rf "$GEN_DIR"

"$JX" --output "$OUT" -t "$PKG" \
  --header-class-name Aom \
  -I "$SDK/usr/include" \
  -I "$PREFIX/include" \
  -l ":$PREFIX/lib/libaom.dylib" \
  --include-function aom_codec_version \
  --include-function aom_codec_version_str \
  --include-function aom_codec_version_extra_str \
  --include-function aom_codec_build_config \
  --include-function aom_codec_iface_name \
  --include-function aom_codec_err_to_string \
  --include-function aom_codec_error \
  --include-function aom_codec_error_detail \
  --include-function aom_codec_destroy \
  --include-function aom_codec_set_option \
  --include-function aom_obu_type_to_string \
  --include-function aom_codec_av1_cx \
  --include-function aom_codec_av1_dx \
  --include-function aom_codec_peek_stream_info \
  --include-function aom_codec_get_stream_info \
  --include-function aom_codec_decode \
  --include-function aom_codec_get_frame \
  --include-function aom_codec_set_frame_buffer_functions \
  --include-function aom_codec_enc_config_default \
  --include-function aom_codec_enc_config_set \
  --include-function aom_codec_get_global_headers \
  --include-function aom_codec_set_cx_data_buf \
  --include-function aom_codec_get_cx_data \
  --include-function aom_codec_get_preview_frame \
  --include-function aom_img_alloc \
  --include-function aom_img_wrap \
  --include-function aom_img_alloc_with_border \
  --include-function aom_img_set_rect \
  --include-function aom_img_flip \
  --include-function aom_img_free \
  --include-function aom_img_plane_width \
  --include-function aom_img_plane_height \
  --include-function aom_img_add_metadata \
  --include-function aom_img_get_metadata \
  --include-function aom_img_num_metadata \
  --include-function aom_img_remove_metadata \
  --include-function aom_img_metadata_alloc \
  --include-function aom_img_metadata_free \
  --include-struct aom_codec_stream_info \
  --include-struct aom_codec_dec_cfg \
  --include-struct aom_fixed_buf \
  --include-struct aom_rational \
  --include-struct cfg_options \
  --include-struct aom_codec_enc_cfg \
  --include-struct aom_metadata \
  --include-struct aom_image \
  --include-constant AOM_CODEC_ABI_VERSION \
  --include-constant AOM_DECODER_ABI_VERSION \
  --include-constant AOM_ENCODER_ABI_VERSION \
  --include-constant AOM_CODEC_OK \
  --include-constant AOM_CODEC_ERROR \
  --include-constant AOM_CODEC_MEM_ERROR \
  --include-constant AOM_CODEC_ABI_MISMATCH \
  --include-constant AOM_CODEC_INCAPABLE \
  --include-constant AOM_CODEC_UNSUP_BITSTREAM \
  --include-constant AOM_CODEC_UNSUP_FEATURE \
  --include-constant AOM_CODEC_CORRUPT_FRAME \
  --include-constant AOM_CODEC_INVALID_PARAM \
  --include-constant AOM_CODEC_LIST_END \
  --include-constant AOM_CODEC_CAP_DECODER \
  --include-constant AOM_CODEC_CAP_ENCODER \
  --include-constant AOM_CODEC_CAP_EXTERNAL_FRAME_BUFFER \
  --include-constant AOM_CODEC_CAP_PSNR \
  --include-constant AOM_CODEC_CAP_HIGHBITDEPTH \
  --include-constant AOM_CODEC_USE_EXPERIMENTAL \
  --include-constant AOM_CODEC_USE_PSNR \
  --include-constant AOM_CODEC_USE_HIGHBITDEPTH \
  --include-constant AOM_FRAME_IS_KEY \
  --include-constant AOM_FRAME_IS_DROPPABLE \
  --include-constant AOM_FRAME_IS_INTRAONLY \
  --include-constant AOM_FRAME_IS_SWITCH \
  --include-constant AOM_EFLAG_FORCE_KF \
  --include-constant AOM_EFLAG_CALCULATE_PSNR \
  --include-constant AOME_SET_CPUUSED \
  --include-constant AOME_SET_CQ_LEVEL \
  --include-constant AV1E_SET_LOSSLESS \
  --include-constant AV1E_SET_ROW_MT \
  --include-constant AV1E_SET_TILE_COLUMNS \
  --include-constant AV1E_SET_TILE_ROWS \
  --include-constant AV1E_SET_TUNE_CONTENT \
  --include-constant AV1E_SET_COLOR_PRIMARIES \
  --include-constant AV1E_SET_TRANSFER_CHARACTERISTICS \
  --include-constant AV1E_SET_MATRIX_COEFFICIENTS \
  --include-constant AV1E_SET_COLOR_RANGE \
  --include-constant AV1_SET_BYTE_ALIGNMENT \
  --include-constant AV1_SET_SKIP_LOOP_FILTER \
  --include-constant AV1D_SET_ROW_MT \
  --include-constant AV1D_SET_IS_ANNEXB \
  --include-constant AV1D_SET_OPERATING_POINT \
  --include-constant AV1D_SET_OUTPUT_ALL_LAYERS \
  --include-constant AV1D_SET_SKIP_FILM_GRAIN \
  --include-constant AOMD_GET_LAST_QUANTIZER \
  --include-constant AOMD_GET_FRAME_FLAGS \
  --include-constant AOM_IMG_FMT_NONE \
  --include-constant AOM_IMG_FMT_YV12 \
  --include-constant AOM_IMG_FMT_I420 \
  --include-constant AOM_IMG_FMT_I422 \
  --include-constant AOM_IMG_FMT_I444 \
  --include-constant AOM_IMG_FMT_NV12 \
  --include-constant AOM_IMG_FMT_I42016 \
  --include-constant AOM_IMG_FMT_YV1216 \
  --include-constant AOM_IMG_FMT_I42216 \
  --include-constant AOM_IMG_FMT_I44416 \
  --include-constant AOM_PLANE_Y \
  --include-constant AOM_PLANE_U \
  --include-constant AOM_PLANE_V \
  --include-constant AOM_BITS_8 \
  --include-constant AOM_BITS_10 \
  --include-constant AOM_BITS_12 \
  --include-constant AOM_CODEC_CX_FRAME_PKT \
  --include-constant AOM_CODEC_STATS_PKT \
  --include-constant AOM_CODEC_FPMB_STATS_PKT \
  --include-constant AOM_CODEC_PSNR_PKT \
  --include-constant AOM_USAGE_GOOD_QUALITY \
  --include-constant AOM_USAGE_REALTIME \
  --include-constant AOM_USAGE_ALL_INTRA \
  --include-constant AOM_RC_ONE_PASS \
  --include-constant AOM_VBR \
  --include-constant AOM_CBR \
  --include-constant AOM_CQ \
  --include-constant AOM_Q \
  --include-constant AOM_KF_AUTO \
  --include-constant AOM_KF_DISABLED \
  --include-constant AOM_MIF_NON_KEY_FRAME \
  --include-constant AOM_MIF_KEY_FRAME \
  --include-constant AOM_MIF_ANY_FRAME \
  "$ROOT/jextract/libaom_api.h"

perl -0777 -pi -e \
  's/static final SymbolLookup SYMBOL_LOOKUP = .*?;/static final SymbolLookup SYMBOL_LOOKUP = io.github.ghosthack.libaomffm.LibaomLibs.lookup();/s' \
  "$GEN_DIR/Aom.java"

if grep -rn "$PREFIX" "$GEN_DIR" >/dev/null; then
  echo "ERROR: build-prefix paths survived in generated stubs"; exit 1
fi
grep -q "LibaomLibs.lookup()" "$GEN_DIR/Aom.java" || { echo "ERROR: lookup patch did not apply"; exit 1; }
echo "Generated $(find "$GEN_DIR" -type f | wc -l | tr -d ' ') files into $GEN_DIR"
