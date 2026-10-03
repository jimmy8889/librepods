# Android UWB implementation and remaining case protocol

The Android fork now has a foreground **UWB experiment** reachable from
**Find my AirPods** and **AirPods experiments**. It uses Android 16+ public
`android.ranging` raw UWB APIs. UWB is optional: older Android versions and
phones without UWB still run the rest of LibrePods.

Implemented:

- Runtime `RANGING` permission, actual platform availability and capability
  callbacks, including disabled radio/regulatory/device-policy states.
- Unicast static or provisioned DS-TWR sessions with negotiated addresses,
  role, session ID, channel/preamble, key material, update rate and slot duration.
- Platform UWB distance and optional azimuth/elevation. Platform/backend
  angles are radians and the UI converts them to degrees. Missing directions
  stay missing. BLE RSSI is never turned into a UWB distance.
- Only medium/high-confidence, finite, fresh, increasing-timestamp distance
  samples are accepted. Readings disappear after two seconds without a fresh
  sample. Peer stop, session close, radio disable, screen exit and timeout clear
  measurements and release the session. Delayed callbacks from old sessions
  cannot repopulate readings.
- Two-minute foreground test limit. Session profiles live only in app memory,
  expire after five minutes before starting, are consumed on start, and are
  cleared on screen exit. LibrePods does not log/import-save session keys.
- Strict bounded session parser and tests for malformed/duplicate/unknown
  fields, address/key mismatch, bad radio values, confidence and timestamp
  filtering, missing directions and invalid numerical results.

## What is not implemented

**Automatic precision finding of an AirPods Pro 3 case is not working yet.**
The case's owner authentication, BLE discovery/identity binding and proprietary
out-of-band UWB negotiation have not been established. The earbuds' saved
Bluetooth address, IRK and BLE signal are not sufficient UWB session data.
No case-start commands or owner keys have been guessed or fabricated.

The Android/iOS interoperability example targets cooperating Nearby Interaction
peers. It does not establish support for the AirPods/Find My owner protocol.
The test importer is for an already negotiated compatible peer and does not
authenticate the claimed identity or make a case begin transmitting.
Find My network access and charging-case ringing are also separate work.

## Lab session input

Import a UTF-8 text file with exactly these fields. Values must come from an
actual out-of-band negotiation, with the remote peer already prepared to range.
The app intentionally does not ship a fabricated working AirPods profile.

| Field | Meaning |
| --- | --- |
| `version` | `1` |
| `sessionId` | Negotiated 32-bit session ID as a signed decimal Java integer |
| `role` | `controller` or `controlee` for this phone |
| `localAddress` | Phone's negotiated UWB address, 4 or 16 hexadecimal digits |
| `peerAddress` | Distinct peer UWB address, same length as local |
| `channel` | `5` or `9`, as negotiated and supported by phone |
| `preamble` | `9` through `12`, as negotiated and supported by phone |
| `configId` | `1` static STS unicast or `3` provisioned STS unicast |
| `sessionKey` | Hex, 8 bytes for static STS vendor ID/IV; 16 or 32 bytes for provisioned STS |
| `updateRate` | Negotiated platform rate `1` through `3` |
| `slotDuration` | Negotiated platform slot `1` or `2` milliseconds |

Use `field=value`, one field per line. Blank lines and `#` comments are allowed;
duplicate/extra/missing fields, escaping and continuation are not. Maximum
input is 4096 bytes. Files remain at the user-selected external location;
clearing the app's in-memory copy does not delete the source file. Protect any
real session file and avoid putting it in source control or shared logs.

## Verification and next hardware work

Build, unit tests and lint must be checked independently of hardware acceptance.
No connected Android phone was available when this implementation was written.
No real distance/direction from the user's case has been observed.

2026-10-03 first device connection: the user's Samsung SM-F976B reports
Android 17 / API 37 and `android.hardware.uwb=true`. The wireless ADB
connection initially disconnected before app checks. A subsequent connection
confirmed `cmd uwb status` reports enabled and the chip's specification report
includes FiRa channels 5 and 9. It reports `aoa_capabilities=0`.
The version-64 UWB lab APK installed successfully over the existing version 63,
and the experiment screen was verified on a later connection. Its actual
application callback reports UWB available, channels 5 and 9, distance supported,
and azimuth/elevation unsupported. Package diagnostics confirm `RANGING` granted.
This verifies access to the public ranging service, not case interoperability.
Foreground radio off/on recovery was subsequently verified: the experiment
displayed "Turn on UWB in phone settings." while disabled, then automatically
returned to "UWB is available" and its capability details after re-enabling.
UWB was restored to enabled. Bringing the experiment back after the app had
been backgrounded also restored its capability display.
No ranging session was started or distance observed. The compatibility script now reads
explicit permission grants from package diagnostics because this firmware
does not provide `pm check-permission`.

2026-10-03 local verification: `assembleFossDebug`, `testFossDebugUnitTest`
(44 tests, zero failures) and `lintFossDebug` passed. Lint reports zero errors
and existing warnings. APK version code is 64, minimum Android API 33; signing
certificate matches the previous delivered debug APK. The build also fixes
an existing API 37 disconnect call on older Android, scopes the connection
broadcast to this app, removes unreachable drawer-notification code and uses
observable UI resources. Fragment lint exclusions apply only to the four
screens that extend ComponentActivity without hosting fragments.

Next: verify permission denial and active-session exit/errors. Verify real raw ranging against
a cooperating negotiated peer, then establish and implement the case's owner
authentication and OOB session exchange. Test actual case distance/direction and
recovery before marking AirPods precision finding supported. The work iPhone
must remain untouched.

## Case protocol investigation prerequisites

The phone checks above do not supply a command that starts the case's radio.
A source review on 2026-10-03 did not identify a verified Android AirPods case
precision-finding handshake. Upstream LibrePods still describes Find My as
planned and needing further reverse-engineering. Public Android/iOS raw ranging
examples configure cooperating Nearby Interaction peers; they are not evidence
of support for an already owned AirPods case.

The next useful evidence is a Bluetooth trace of a successful precision-finding
start/stop sequence with this case, using a personal compatible Apple device
that can already find it, or an existing trace. Capture setup must be established
before requesting the user enable logging. No work-iPhone changes, case reset,
ownership transfer or guessed GATT writes are needed for the current investigation.
Capture only the controlled test and keep any owner/session secrets private.

A case implementation needs evidence for BLE identity binding, authentication,
start/stop commands, session parameter/key negotiation and recovery. Implement
those from observed exchanges, then verify Android-to-case distance before
enabling automatic precision finding. Shizuku can provide privileged Android
diagnostics but does not itself negotiate Apple case sessions or supply owner
credentials. The confirmed public API on this phone currently exposes distance
capability without azimuth/elevation capability.

For Android-only investigation, the UWB experiment now includes **Inspect
paired BLE interface**. It scans Apple advertisements for up to 25 seconds,
requires a connectable resolvable private address matching the saved AirPods
IRK, and discovers that peer's GATT service/characteristic UUIDs and property
flags. It does not connect to unassociated nearby devices, read characteristic
values, write commands, subscribe to notifications, bond or change pairing.
The matching key is cleared from probe memory after discovery selection or
exit; addresses and advertisement payloads are not logged or exported.
Service discovery is bounded and disconnects/closes the GATT client on finish,
failure, timeout or screen exit. A matching peer may be the earbuds; these
metadata are not proof of case identity, ownership authentication or a working
UWB exchange. Closed-case Find My advertisements may use a different identity
and may not be discoverable with the existing earbud IRK.

2026-10-03 Android-only probe verification: version 65 installed successfully
on the user's SM-F976B with Bluetooth scan/connect and ranging permissions
granted. The first 25-second inspection ended with no connectable BLE peer
resolved by the saved AirPods IRK. No service UUIDs or case session data were
obtained, and case presence/lid state was not independently confirmed. This
negative scan is not proof that the case is absent or cannot support ranging.
Screen-exit cleanup was also verified: an active inspection was cleared when
the app was backgrounded, and returning showed the idle inspection state.
The final build, all 44 unit tests and lint passed (zero errors, 272 warnings).
The APK signing certificate matches the preceding release. SHA-256:
`50d3e3ac55a3b749c96094f08caea242d2c9038fd97db1259a50c365c9a6fe80`.

Sources:

- [Android ranging and iOS OOB interoperability](https://developer.android.com/develop/connectivity/ranging)
- [Android RangingData timestamps and nullable measurements](https://developer.android.com/reference/android/ranging/RangingData)
- [AOSP UWB backend angle units](https://android.googlesource.com/platform/packages/modules/Uwb/+/refs/heads/main/ranging/uwb_backend/src/com/android/ranging/uwb/backend/internal/RangingPosition.java)
- [Apple Nearby Interaction](https://developer.apple.com/documentation/nearbyinteraction)
- [Upstream LibrePods Find My status](https://github.com/librepods-org/librepods#find-my)
- [Shizuku system API access](https://shizuku.rikka.app/introduction/)
