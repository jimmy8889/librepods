# Android fork status

## Available in the FOSS flavor

All upgrade-gated controls are available immediately and do not expire.

App Settings → Find my AirPods provides:

- A 15-second Bluetooth discovery search for the saved AirPods address and signal
  strength when reported. This is not precision distance or direction finding.
  AirPods must be discoverable; a closed case or an undiscoverable pair may not
  respond. Discovery can briefly affect audio.
- Separate left/right five-second ringing over an existing, verified A2DP media
  route. This does not ring the charging case. Ringing stops when leaving the
  screen or losing the route; system media volume is not raised.
- A locally saved, per-device connection timestamp and phone location, with the
  location's own timestamp and accuracy. Location is opt-in. The connection
  handler uses only a location fix from the previous two minutes if permission
  already exists; the finding screen can request a fresh fix while connected.
  Saved coordinates can be opened in an installed maps app. Android backup
  settings apply to saved preferences.

No background location permission, Apple account login, or Find My network is used.

## Experimental heart rate and microphone

App Settings → AirPods experiments now provides two explicit foreground tests:

- Heart rate: negotiates the RTBuddy heart-rate service, starts a one-second
  sample stream, and displays accepted BPM values. It rejects malformed frames,
  acknowledgements, duplicates, warm-up samples and low-quality samples. Readings
  clear after four seconds without an accepted sample. Startup retries are bounded;
  each attempt allows 30 seconds and startup can take up to two minutes. Wear at least one AirPod Pro 3.
- Microphone: requests the AACP high-resolution microphone stream and decodes
  AAC-ELD through Android MediaCodec. A ten-second PCM WAV sample can be played
  or explicitly shared. The actual decoder output sample rate is shown. Music
  may remain playing to test concurrent A2DP playback. Conversation awareness
  pauses during capture and its prior setting is restored on the same connection.

Both tests stop when leaving the screen. Microphone permission is requested before
recording. Audio stays in private app cache unless explicitly shared; starting a
new recording replaces the prior sample. Audio and RTBuddy packets bypass normal
raw-packet logging and broadcasts. No health data is uploaded or exported to
Health Connect. Heart-rate quality interpretation is experimental and is not
validated for medical use.

Protocol work is adapted from GPL-licensed upstream contributions:

- thibaup, [PR 702](https://github.com/librepods-org/librepods/pull/702),
  revision `4d27253b910c10fe86b50fc491ff2331086e3b1e`: RTBuddy transport/parser.
- IvanChanPing, [PR 723](https://github.com/librepods-org/librepods/pull/723),
  revision `5afad4dc4de5016a57c1e521b27d044d2e738047`: Android AAC-ELD framing/decoder.
- [PR 655](https://github.com/librepods-org/librepods/pull/655): Linux microphone
  experiments and concurrent playback findings.

These are experimental upstream contributions, not a guarantee of compatibility
with this phone or firmware. Local tests cover parsing and filtering; there is no
connected phone here to validate sensor readings, decoder availability or sound.

## Remaining: high-quality audio in phone/video calls

The microphone test does not expose its PCM stream as an Android system input.
Phone, WhatsApp and other video-call apps still use Android's existing Bluetooth
call routing. A separate privileged audio-routing/HAL integration or cooperation
from the calling app is required. Root status and device-specific feasibility
must be established before attempting that integration. Choosing AAC for media
playback alone does not change the call microphone.

See [upstream issue 720](https://github.com/librepods-org/librepods/issues/720).

## Build

This workspace has Java 21 and Android command-line tools installed at
`/home/codex/Android/Sdk`, with SDK 37.0, NDK 30.0.14904198 and CMake 3.22.1.
Source `/home/codex/.config/librepods/android-env.sh`, then run from `android`:

```sh
./gradlew assembleFossDebug testFossDebugUnitTest --max-workers=2 -Dorg.gradle.jvmargs='-Xmx3g -Dfile.encoding=UTF-8'
```

Test on physical AirPods Pro 3 before treating finding/ringing as verified.

## Validation limits

The FOSS debug APK builds successfully. All 18 local unit tests pass, covering
RTBuddy parsing, malformed inputs, service selection, sample filtering, AAC-ELD
packet framing and WAV generation. No Android
phone is attached to this workspace, so connection, ringing, and location behavior
still require testing on the owner's device. Full-project lint is not clean:
existing upstream findings include API-level compatibility, widget tint checks,
and an implicit service broadcast. These are not represented as passing checks.

## Device feedback and heart-rate troubleshooting (2026-09-22)

The owner reports successful microphone recording during audio playback. Heart
rate with one earbud ended with "No sustained readings"; its cause is not yet
confirmed. The follow-up build uses the payload's per-sample counter instead of
its enclosing message sequence for duplicate filtering, allows 30 seconds per
startup attempt, and displays sensor-packet, parsed-sample, rejected-frame,
warm-up, quality and duplicate counts. The diagnostics contain no raw biometric
payloads. These counters distinguish a silent transport from parser or quality
rejection without treating acknowledgements as measurements. Counter semantics
are based on the captures discussed in upstream PR 702, not a capture from this
owner's phone. The added regression tests cover constant envelope sequences and
8-bit sample-counter wraparound. Phone/video-call input routing remains pending.

ADB logs from the owner's initial test confirm rejected heart-rate-related
responses, with no accepted samples. The follow-up diagnostics distinguish a
non-measurement payload length, out-of-range reading, and unrecognized sensor
status without logging the raw payload or weakening validation.

Live ADB follow-up on 2026-09-22 (build `d0b8811`): the start request
was written successfully to fallback service 19. The parser received two-byte
payloads around start and stop, with no validated 18-byte measurement. The latest
observed run stopped after about 22 seconds; earlier complete retry runs also
produced no validated sample. This narrows the observed failure to activation,
service selection or an unsupported response format; it does not establish that
the accessory lacks HR support. Duplicate filtering was corrected independently
and did not resolve the observed no-measurement behavior. The meaning of the
two-byte responses remains unknown. Do not interpret them as BPM.

## Battery follow-up (2026-09-22)

The owner's Samsung reported high battery use. Its battery statistics attributed
about 11h26m of Bluetooth scanning to LibrePods; a CPU snapshot showed no busy
loop. The inherited service used continuous SCAN_MODE_LOW_LATENCY. Routine BLE
scanning now requests SCAN_MODE_LOW_POWER instead, with the existing manufacturer
filter. Starts are idempotent and serialized; cleanup callbacks are removed on
stop or scan failure. The L2CAP receive buffer is reused instead of allocating
64 KiB for every packet. Nearby detection may respond more slowly. Actual
battery savings and the disappearance of Samsung's historical warning require
normal-use observation; these are not claimed as measured improvements.

Android scan-mode reference:
https://developer.android.com/reference/android/bluetooth/le/ScanSettings#SCAN_MODE_LOW_POWER

## Reconnect widget (2026-09-26)

Add **LibrePods → Reconnect AirPods** from the launcher's widget picker. The
compact widget reconnects the saved paired AirPods using the existing AACP and
audio connection path, starting the foreground service when needed. It checks
Bluetooth permissions, saved setup and Bluetooth power before starting. Repeated
taps during a manual connection attempt are ignored. It adds no periodic
updates, scanning or polling. Nearby-device permissions and initial pairing
must already be configured in the app.

## Quick Settings controls (2026-09-26)

In the phone's Quick Settings editor, add **AirPods modes** and **Reconnect
AirPods**. The existing ANC Mode tile keeps its component identity but now opens
a selection dialog rather than cycling blindly. Pick Transparency or Noise
cancellation, plus Adaptive on supported devices and Off when enabled in app
settings. The dialog also offers Reconnect. The separate reconnect tile invokes
the same guarded connection path as the home-screen widget and starts the
foreground service when necessary. Locked-phone actions request unlock first.

Tile status observes app-private connection and mode broadcasts only while Quick
Settings is listening. There are no timers or additional scans. Disconnected tiles
remain tappable so reconnection is available. No physical phone is attached for
this build; Samsung panel presentation and actual tile taps still need device
validation. Build, signature checks and 20 unit tests pass, including supported
mode filtering and AACP mode values.

## Battery in Quick Settings; no drawer notifications (2026-09-26)

Both tiles show L/R/C battery percentages and charging indicators when connected.
The mode picker repeats the full summary in its title in case Samsung truncates
the tile subtitle. Missing, disconnected or invalid readings show a dash; a
real zero remains 0%. Updates use existing battery broadcasts, without polling.

The separate battery notification is removed and cleared on service startup.
This fork no longer declares or requests POST_NOTIFICATIONS. On Android 13+
(the app requires Android 13), Android suppresses ordinary notifications and
foreground-service notices from the notification drawer without this permission.
The service still supplies its mandatory silent notification to Android and
remains visible in the system Active apps/Task Manager list. Existing user
notification preferences are not changed with hidden APIs. On-device behavior
after upgrade remains to be checked; build, signature and 22 unit tests pass.

Reference: https://developer.android.com/develop/ui/compose/notifications/notification-permission
