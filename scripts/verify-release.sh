#!/usr/bin/env bash
set -euo pipefail

[[ -n "${GROVE_SIGNING_PASSWORD:-}" ]] || {
  echo 'GROVE_SIGNING_PASSWORD is required for release artifacts' >&2
  exit 1
}
apk=app/build/outputs/apk/release/app-release.apk
aab=app/build/outputs/bundle/release/app-release.aab
test -s "$apk"
test -s "$aab"
tools_dir="$(find "$ANDROID_HOME/build-tools" -name apksigner -type f | sort -V | tail -1)"
[[ -n "$tools_dir" ]] || { echo 'apksigner missing' >&2; exit 1; }
tools_dir="$(dirname "$tools_dir")"
"$tools_dir/apksigner" verify --print-certs "$apk" |
  grep -Fi 'eec2c5ab1ff523112a52e5830d5b50169b7f7475b17daa5b7422c0f497147ff3'
jarsigner -verify "$aab" | grep -F 'jar verified.'
keytool -printcert -jarfile "$aab" |
  grep -Fi 'EE:C2:C5:AB:1F:F5:23:11:2A:52:E5:83:0D:5B:50:16:9B:7F:74:75:B1:7D:AA:5B:74:22:C0:F4:97:14:7F:F3'
"$tools_dir/zipalign" -c -P 16 -v 4 "$apk"
readelf="$ANDROID_HOME/ndk/27.3.13750724/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
test -x "$readelf"
temporary="$(mktemp -d)"
trap 'rm -rf "$temporary"' EXIT
for abi in arm64-v8a armeabi-v7a x86_64; do
  unzip -Z1 "$apk" | grep -Fx "lib/$abi/libgrove_core.so"
  unzip -Z1 "$aab" | grep -Fx "base/lib/$abi/libgrove_core.so"
  unzip -p "$apk" "lib/$abi/libgrove_core.so" > "$temporary/$abi.so"
  test -s "$temporary/$abi.so"
  "$readelf" -lW "$temporary/$abi.so" |
    awk '$1 == "LOAD" { print $NF }' > "$temporary/alignments"
  test -s "$temporary/alignments"
  while read -r alignment; do test "$((alignment))" -ge 16384; done < "$temporary/alignments"
done
sha256sum "$apk" "$aab" > release-checksums.txt
{
  echo "commit=$(git rev-parse HEAD)"
  echo "certificate_sha256=EE:C2:C5:AB:1F:F5:23:11:2A:52:E5:83:0D:5B:50:16:9B:7F:74:75:B1:7D:AA:5B:74:22:C0:F4:97:14:7F:F3"
  cat release-checksums.txt
} > release-provenance.txt
