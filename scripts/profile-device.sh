#!/usr/bin/env bash
set -euo pipefail

# Local, explicit physical-device capture. No upload, provider reads or emulator.
PACKAGE="${1:-tech.granet.grove.test}"
LABEL="${2:-manual}"
SECONDS_TO_RECORD="${3:-30}"
[[ "$PACKAGE" == tech.granet.grove || "$PACKAGE" == tech.granet.grove.test ]] || exit 2
[[ "$LABEL" =~ ^[a-zA-Z0-9_-]+$ ]] || exit 2
[[ "$SECONDS_TO_RECORD" =~ ^[0-9]+$ && "$SECONDS_TO_RECORD" -ge 5 && "$SECONDS_TO_RECORD" -le 120 ]] || exit 2
command -v adb >/dev/null
adb get-state >/dev/null
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]] || { echo 'Physical device required' >&2; exit 2; }
OUTPUT="verification/performance/device-$LABEL-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$OUTPUT"
{
    git rev-parse HEAD
    adb shell getprop ro.product.model
    adb shell getprop ro.build.version.sdk
    adb shell getprop ro.product.cpu.abilist
    adb shell dumpsys package "$PACKAGE" | sed -n '/versionCode=/p; /versionName=/p'
} > "$OUTPUT/identity.txt"
adb shell dumpsys meminfo "$PACKAGE" > "$OUTPUT/memory-before.txt"
adb shell dumpsys gfxinfo "$PACKAGE" reset > /dev/null
echo "Exercise $LABEL in Grove for $SECONDS_TO_RECORD seconds."
TRACE="/data/local/tmp/grove-$LABEL.pftrace"
adb shell perfetto -o "$TRACE" -t "${SECONDS_TO_RECORD}s" -a "$PACKAGE" sched freq idle am wm gfx view binder_driver
adb pull "$TRACE" "$OUTPUT/trace.pftrace"
adb shell rm "$TRACE"
adb shell dumpsys meminfo "$PACKAGE" > "$OUTPUT/memory-after.txt"
adb shell dumpsys gfxinfo "$PACKAGE" framestats > "$OUTPUT/frames.txt"
echo "Saved $OUTPUT; record corpus/build type and repeat against the baseline."
