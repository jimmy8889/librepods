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

The FOSS debug APK builds successfully. All 17 local unit tests pass, covering
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
