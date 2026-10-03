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

2026-10-03 local verification: `assembleFossDebug`, `testFossDebugUnitTest`
(44 tests, zero failures) and `lintFossDebug` passed. Lint reports zero errors
and existing warnings. APK version code is 64, minimum Android API 33; signing
certificate matches the previous delivered debug APK. The build also fixes
an existing API 37 disconnect call on older Android, scopes the connection
broadcast to this app, removes unreachable drawer-notification code and uses
observable UI resources. Fragment lint exclusions apply only to the four
screens that extend ComponentActivity without hosting fragments.

Next: verify the actual phone's reported UWB capability, permission grant/denial,
radio off/on, foreground exit and session errors. Verify real raw ranging against
a cooperating negotiated peer, then establish and implement the case's owner
authentication and OOB session exchange. Test actual case distance/direction and
recovery before marking AirPods precision finding supported. The work iPhone
must remain untouched.

Sources:

- [Android ranging and iOS OOB interoperability](https://developer.android.com/develop/connectivity/ranging)
- [Android RangingData timestamps and nullable measurements](https://developer.android.com/reference/android/ranging/RangingData)
- [AOSP UWB backend angle units](https://android.googlesource.com/platform/packages/modules/Uwb/+/refs/heads/main/ranging/uwb_backend/src/com/android/ranging/uwb/backend/internal/RangingPosition.java)
- [Apple Nearby Interaction](https://developer.apple.com/documentation/nearbyinteraction)
