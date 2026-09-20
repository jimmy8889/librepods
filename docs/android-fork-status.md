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

## Not implemented: heart-rate monitoring

Upstream marks AirPods Pro 3 as HRM-capable, but supplies no verified heart-rate
transport or decoder. The capability bit is not a heart-rate implementation.
Do not display synthesized BPM or decode arbitrary protocol bytes as measurements.

Next requirement: a consented Bluetooth trace from the owner's AirPods Pro 3 and
an Apple device, with matching observed heart-rate readings, to establish the
activation command and measurement transport. See upstream issue 308:
https://github.com/librepods-org/librepods/issues/308

## Not implemented: high-quality two-way call audio

Ordinary Bluetooth call routing continues to be controlled by Android and the
calling app. Upstream describes Apple's high-quality microphone path as AACP
alongside A2DP. LibrePods has no decoder or Android audio input integration for
that stream. This cannot be supplied by a UI toggle or by merely choosing AAC.

Next requirements: verified stream negotiation, codec/framing and decoding,
then an Android input path usable by phone/video-call apps. System/root-level
integration may be necessary. Standard LE Audio only helps when both endpoints
actually expose a compatible audio profile; Bluetooth version alone is not proof.

Source: https://github.com/librepods-org/librepods#high-quality-two-way-audio

## Build

This workspace has Java 21 and Android command-line tools installed at
`/home/codex/Android/Sdk`, with SDK 37.0, NDK 30.0.14904198 and CMake 3.22.1.
Source `/home/codex/.config/librepods/android-env.sh`, then run from `android`:

```sh
./gradlew assembleFossDebug --max-workers=2 -Dorg.gradle.jvmargs='-Xmx3g -Dfile.encoding=UTF-8'
```

Test on physical AirPods Pro 3 before treating finding/ringing as verified.

## Validation limits

The FOSS debug APK builds successfully and its APK signature verifies. No Android
phone is attached to this workspace, so connection, ringing, and location behavior
still require testing on the owner's device. Full-project lint is not clean:
existing upstream findings include API-level compatibility, widget tint checks,
and an implicit service broadcast. These are not represented as passing checks.
