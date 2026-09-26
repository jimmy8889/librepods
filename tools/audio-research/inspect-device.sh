#!/usr/bin/env bash
# Read-only: no settings, grants, audio routes or calls are changed.
set -euo pipefail
adb_args=()
if [[ $# -gt 0 ]]; then adb_args=(-s "$1"); fi
adb "${adb_args[@]}" get-state >/dev/null
for prop in ro.product.model ro.product.device ro.build.version.release ro.build.version.sdk ro.build.version.security_patch; do
    printf '%s: ' "$prop"
    adb "${adb_args[@]}" shell getprop "$prop"
done
for permission in MODIFY_AUDIO_ROUTING CALL_AUDIO_INTERCEPTION CAPTURE_AUDIO_OUTPUT; do
    printf '%s: ' "$permission"
    adb "${adb_args[@]}" shell pm check-permission "android.permission.$permission" com.android.shell
done
printf '\nShizuku installation:\n'
adb "${adb_args[@]}" shell pm path moe.shizuku.privileged.api
printf '\nAudio hardware services:\n'
adb "${adb_args[@]}" shell service list | grep -i audio || true
printf '\nAvailable audio policy device types (no recording):\n'
adb "${adb_args[@]}" shell dumpsys media.audio_policy | grep -E 'AUDIO_DEVICE_|TELEPHONY|REMOTE_SUBMIX' || true
