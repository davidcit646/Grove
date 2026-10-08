#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ndk_dir="${ANDROID_NDK_HOME:-}"
if [[ -z "$ndk_dir" ]]; then
  sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  [[ -n "$sdk_dir" ]] || { echo 'Set ANDROID_NDK_HOME or ANDROID_HOME.' >&2; exit 1; }
  ndk_dir="$sdk_dir/ndk/27.3.13750724"
fi
toolchain="$ndk_dir/toolchains/llvm/prebuilt/linux-x86_64/bin"
[[ -d "$toolchain" ]] || { echo "Android NDK toolchain missing: $toolchain" >&2; exit 1; }

export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$toolchain/aarch64-linux-android31-clang"
export CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER="$toolchain/armv7a-linux-androideabi31-clang"
export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$toolchain/x86_64-linux-android31-clang"
# NDK r27 needs explicit ELF alignment on 16 KB page-size devices.
page_flags="-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS="$page_flags"
export CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_RUSTFLAGS="$page_flags"
export CARGO_TARGET_X86_64_LINUX_ANDROID_RUSTFLAGS="$page_flags"

cd "$project_dir/rust/grove-core"
for target in aarch64-linux-android armv7-linux-androideabi x86_64-linux-android; do
  rustup target add "$target"
  cargo build --locked --release --target "$target"
done

output="$project_dir/app/build/rustJniLibs"
mkdir -p "$output/arm64-v8a" "$output/armeabi-v7a" "$output/x86_64"
cp target/aarch64-linux-android/release/libgrove_core.so "$output/arm64-v8a/"
cp target/armv7-linux-androideabi/release/libgrove_core.so "$output/armeabi-v7a/"
cp target/x86_64-linux-android/release/libgrove_core.so "$output/x86_64/"
