#!/usr/bin/env bash
# Read-only compatibility report. Does not collect health records, account details,
# Bluetooth addresses, locations, logs, or identifiers such as the device serial.
set -euo pipefail
adb_args=()
if [[ $# -gt 0 ]]; then adb_args=(-s "$1"); fi
adb "${adb_args[@]}" get-state >/dev/null
# Some firmware (including the tested Samsung Android 17 build) has no
# `pm check-permission` command. Read the explicit grant in the package report.
permission_state() {
    local permission="$1" package="$2" report
    report=$(adb "${adb_args[@]}" shell dumpsys package "$package") || return
    awk -v permission="$permission:" '
        $1 == permission && /granted=true/ { granted = 1 }
        $1 == permission && /granted=false/ { denied = 1 }
        END {
            if (granted) print "granted"
            else if (denied) print "denied"
            else print "no explicit grant reported"
        }
    ' <<< "$report"
}
for prop in ro.product.manufacturer ro.product.model ro.build.version.release ro.build.version.sdk; do
    printf '%s: ' "$prop"
    adb "${adb_args[@]}" shell getprop "$prop"
done
printf '\nUWB advertised by firmware:\n'
adb "${adb_args[@]}" shell pm has-feature android.hardware.uwb
printf '\nLibrePods unified ranging permission (Android 16+):\n'
permission_state android.permission.RANGING me.kavishdevar.librepods
printf '\nRelevant installed package versions:\n'
for package in me.kavishdevar.librepods com.sec.android.app.shealth com.google.android.healthconnect.controller com.google.android.apps.healthdata com.android.healthconnect.controller moe.shizuku.privileged.api af.shizuku.plus.api; do
    printf '%s\n' "$package"
    adb "${adb_args[@]}" shell dumpsys package "$package" | awk '/versionName=|versionCode=/{if (++count <= 2) print}'
done
printf '\nLibrePods Health Connect write permissions:\n'
for permission in WRITE_HEART_RATE WRITE_EXERCISE; do
    printf '%s: ' "$permission"
    permission_state "android.permission.health.$permission" me.kavishdevar.librepods
done
printf '\nSamsung Health read permissions:\n'
for permission in READ_HEART_RATE READ_EXERCISE; do
    printf '%s: ' "$permission"
    permission_state "android.permission.health.$permission" com.sec.android.app.shealth
done
printf '\nA granted permission does not prove Samsung imported a record.\n'
printf 'A UWB feature does not prove Apple accessory protocol compatibility.\n'
