# System-wide AirPods microphone: investigation, 2026-09-26

Status: source-level candidate, NOT enabled or verified on Samsung. No phone is
connected. The previously used ADB endpoint refused connection. No system changes
have been attempted and no replacement APK is needed for this investigation.

The earlier statement that this necessarily requires a custom audio HAL was too
strong. AOSP Android 17 contains two privileged routes worth testing before root:

1. AudioMixingRule MIX_ROLE_INJECTOR, a recorder-targeted AudioMix with LOOP_BACK,
   and AudioPolicy.createAudioTrackSource. PCM written to that track feeds matching
   recording clients. Start with an explicit test-app UID, not a blanket rule.
2. AudioManager.getCallUplinkInjectionAudioTrack for an active communication/call
   mode. Requires CALL_AUDIO_INTERCEPTION. Cellular calls additionally require
   isPstnCallAudioInterceptable(); AOSP checks for telephony TX and RX devices.
   Mere device presence is not proof that Samsung uplink injection works.

AOSP's Shell manifest requests MODIFY_AUDIO_ROUTING and CALL_AUDIO_INTERCEPTION.
Both are signature/privileged/role permissions, not ordinary runtime grants.
Shizuku UserService can run as shell UID 2000 after ADB startup. Consequently a
shell-hosted bridge is a plausible non-root path. Manifest declarations alone do
not prove the Samsung shell has these grants, that hidden APIs are callable, or
that audio-policy/native/vendor checks permit the operation. Verify on device.
A normal app cannot gain these permissions just by adding them to its manifest.

## Proposed proof sequence

- Run tools/audio-research/inspect-device.sh [adb-serial]. This reads model/API,
  shell permission results, Shizuku installation and relevant audio devices only.
- In a shell-hosted diagnostic process, query PSTN accessibility and test creation
  and immediate cleanup of a recorder mix targeting a dedicated test recorder.
- Feed a known PCM test signal into that recorder and verify actual received PCM;
  registration success alone is insufficient. Never inject into an ongoing call
  during this initial test.
- Replace the signal with LibrePods' existing AAC-ELD decoder output through an
  authenticated local pipe. Resample the reported decoder output rate as needed;
  do not assume its 48 kHz coding rate equals PCM output (may be 64 kHz).
- Test an explicitly selected voice app, then a deliberate test call separately.
  Confirm the remote listener receives AirPods audio, not the phone microphone.
- Verify high-quality output too: injection may work while the destination app
  still switches output to Bluetooth SCO. Microphone injection alone is not proof
  of high-quality two-way audio. Test latency, echo, mute, routing and reconnection.
- Only then consider general app coverage. Stop and unregister on AirPods loss,
  bridge death or user disable; restore ordinary microphone behavior. Stream only
  while needed, respect microphone privacy/mute, and avoid saving call content.

Cellular network codecs still limit end-to-end quality. App-specific input device
choices, native recording APIs and Samsung policy may require separate handling.
Root/HAL work is a fallback investigation, not a current requirement or guarantee.

## Primary sources inspected

- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/media/java/android/media/audiopolicy/AudioMixingRule.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/media/java/android/media/audiopolicy/AudioPolicy.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/media/java/android/media/AudioManager.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/services/core/java/com/android/server/audio/AudioService.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/core/res/AndroidManifest.xml
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/packages/Shell/AndroidManifest.xml
- https://github.com/RikkaApps/Shizuku-API/blob/master/README.md
- https://shizuku.rikka.app/guide/setup/
