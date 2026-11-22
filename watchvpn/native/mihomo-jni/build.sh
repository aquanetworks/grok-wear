#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$ROOT/../../src/main/jniLibs/armeabi-v7a/libmihomo-jni.so}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-${ANDROID_HOME:+$ANDROID_HOME/ndk/27.2.12479018}}}"
if [[ -z "${NDK}" || ! -d "${NDK}" ]]; then
  NDK="/home/ubuntu/android-sdk/ndk/27.2.12479018"
fi
PREBUILT="${NDK}/toolchains/llvm/prebuilt/linux-x86_64"
CC="${PREBUILT}/bin/armv7a-linux-androideabi30-clang"
CXX="${PREBUILT}/bin/armv7a-linux-androideabi30-clang++"
if [[ ! -x "$CC" ]]; then
  echo "missing NDK clang: $CC" >&2
  exit 1
fi
mkdir -p "$(dirname "$OUT")"
export GOOS=android
export GOARCH=arm
export GOARM=7
export CGO_ENABLED=1
export CC CXX
export CGO_CFLAGS="-O2 -fPIC -I${PREBUILT}/sysroot/usr/include"
export CGO_LDFLAGS="-Wl,-soname,libmihomo-jni.so -Wl,--hash-style=both -llog"
export GOFLAGS="-buildvcs=false"
cd "$ROOT"
go mod tidy
go build -tags with_gvisor -trimpath \
  -ldflags "-s -w -X github.com/metacubex/mihomo/constant.Version=1.19.34 -extldflags=-Wl,-soname,libmihomo-jni.so" \
  -buildmode=c-shared \
  -o "$OUT" .
rm -f "${OUT%.so}.h" "$ROOT/libmihomo-jni.h"
file "$OUT"
ls -lh "$OUT"
