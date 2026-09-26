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

## Combined AirPods control row (2026-09-26)

The existing reconnect widget is upgraded in place to **AirPods controls**: one
rounded horizontal row with L/R/case battery levels, Transparency (Hear), Noise
Cancellation (ANC), Adaptive (Auto), Off where enabled, and reconnect (Connect).
The active mode has a light selected background. Unsupported Adaptive and disabled
Off controls are hidden; mode controls are disabled when disconnected. Tapping
the battery area opens LibrePods. Reconnect retains the guarded service-start
path. Unknown battery values remain dashes; charging indicators are retained.

Add AirPods controls from the widget picker, or resize/re-add an existing small
Reconnect widget to a full-width row. The suggested size is five cells wide and
one high; width is resizable with a 300dp minimum. Battery, mode, connection and
Off-setting events refresh the row. Duplicate snapshots are suppressed, there
are no periodic updates or extra scans, and notification drawer cards stay off.
No phone is currently attached to validate launcher sizing or widget taps.

## Single control surface (2026-09-26)

Removed the old Battery and Noise Control widget providers and both Quick
Settings tiles, plus their unused activity and service/widget implementations.
Only the combined AirPods controls row remains in the widget picker. Its existing
ReconnectWidget component is retained so the previous combined row updates in
place. Old separate widgets may leave launcher placeholders to remove.

## Combined Quick Settings control (2026-09-26)

Corrected the control location: one AirPods controls Quick Settings tile now
opens the combined battery/modes/reconnect row. The home-screen row remains
available; the old separate tiles and widgets remain removed. App Settings
includes an Add AirPods controls to Quick Settings action using Android's tile
placement prompt. Status listeners run only while the tile is visible, with
no periodic polling or extra Bluetooth scans. Phone validation remains pending.

## Home widget placement compatibility (2026-09-26)

User reports Lawnchair 15 permission approval followed by no widget and requests
a narrower row for a four-column grid. No connected
phone/logs yet, so the root cause remains unconfirmed. Changed the default span
from five to four columns, minimum width from 300dp to 280dp, and minimum height
to 68dp to accommodate the 52dp buttons plus padding. Added a distinct home-screen
pin action in app settings, separate from Quick Settings. It uses the launcher's
pin flow and confirms success only from the completion callback, which also
refreshes widget content. Unsupported/failed requests show manual placement
instructions. No extra permissions are requested.

## Heart-rate sensor-channel investigation (2026-09-26)

The owner confirms the combined widget works and requested renewed HR work.
ADB at the previous wireless endpoint timed out; a new port was requested.
No live BPM has yet been verified on this pair.

Reference: https://github.com/librepods-org/librepods/pull/702 (SAGIRIxr's
first-hand captures report ACK-only failures until RTBuddy produces other sensor
data; this is a hypothesis to test here, not a verified fix).

The foreground experiment now distinguishes service-setting acknowledgements
(field 9) from HR payloads and counts opaque sensor-stream frames (field 3)
without interpreting them as BPM. Metadata discovery is limited to descriptor
field 5 and recognizes devmotion6 independently of HeartRateService. Motion
service IDs are never guessed. After the unchanged initial HR attempt, retries
briefly start an advertised motion service at 40 ms, wait at most eight seconds
for channel data, and stop that probe before starting HR. Existing app head
tracking is preserved; leaving the test also cleans up an owned motion probe.
No continuous background HR or extra BLE scans were added. Quality and payload
validation remain unchanged. Added parser/diagnostic/control regression tests.
The test screen also has Copy sensor diagnostics so the owner can return
counts/status without sharing raw packets or BPM values.

## Connection popup preference (2026-09-26)

Removed the bottom popup and its setting. The existing top popup now has a
two-second deadline, including its exit animation; touches do not extend it.
The deadline removes the overlay immediately and stops video playback, even
if an animated dismissal is in progress. Heart-rate investigation is unchanged.

## Five-second animated popup (2026-09-26)

Updated the top popup to a five-second total display period. The original 700 ms
entrance animation remains; automatic dismissal now calls the animated close
path at 4.3 seconds, allowing its 700 ms exit animation to finish at five seconds.
The bottom popup remains removed. Timing has not been measured on the phone.

## Workouts, periodic readings and movement boost (2026-09-26)

Owner confirmed 94 parsed / 90 accepted HR samples (four warm-up), advertised HR
service 20, motion service 16. This is the first user-confirmed working stream.

Added Settings → Heart rate → Workouts and Samsung Health. Manual walking,
running, cycling and other workouts continue in the existing foreground service
when the activity closes or the screen turns off. Accepted samples are saved
individually on a serialized SQLite worker in noBackupFilesDir. Disconnect or
service shutdown ends a session; process-restart recovery closes interrupted
sessions at their last stored reading. No invented calories, distance or GPS.

Health Connect 1.1.0 integration requests only WRITE_HEART_RATE and WRITE_EXERCISE,
with Android 13 rationale and Android 14+ permission-usage entry points. Finished
workouts export an ExerciseSessionRecord and minute-bucketed HeartRateRecords.
Stable client IDs/version make partial export retries idempotent. Local delete
does not delete external copies; the UI states this. Samsung Health must separately
be allowed to read the records. This is saved-data syncing, NOT a live sensor
inside Samsung Health on the same phone. No supported local live-sensor injection
API was found. Sources:
https://developer.samsung.com/health/blog/en/accessing-samsung-health-data-through-health-connect
https://developer.samsung.com/health/accessory/accessory-faq.html

Optional daily sampling (default OFF; 15 minutes, configurable 5/15/30) requests
up to five accepted samples with a 30-second cap while connected and worn. Phone
sleep can defer checks; no exact alarms/wake locks/new Bluetooth scans are added.
Automatic samples export when Health Connect write permissions are available,
otherwise stay local for explicit retry. Empty automatic sessions are discarded.

Optional movement boost uses the phone TYPE_STEP_DETECTOR and Physical activity
permission, with the health foreground-service type enabled only when permitted.
60 steps within 90 seconds enters frequent recording (~1 Hz); 3 minutes without
steps exits. This detects walking/running-like movement, not cycling or strength
training. Manual recording takes priority; detected movement exports HR only and
does not create a guessed workout. A user stop suppresses auto-resume until rest.
https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion

Validation covers motion entry/exit, isolated/duplicate steps, inactivity gaps,
monotonic session timestamps, stable export IDs, gaps, sample bounds and automatic
readings not producing exercise sessions. Actual background behavior, permissions
and Samsung Health ingestion still require testing on the owner's phone.

### Latest recorded heart rate in the control row

Replaces the Off shortcut in the 4×1 home widget and its shared Quick Settings
popup with the latest persisted BPM, 24-hour recording time and date (day/month).
Tap the reading to open workouts. Empty history shows a dash, never a guessed BPM.
Readings remain visible when disconnected and are restored asynchronously after
process restart. Deleting the newest recording falls back to the preceding saved
sample; deleting all history clears the display. SQLite v2 adds a timestamp index
without removing data. Updates follow successful saved-sample changes, without
additional sensor sampling, scanning or periodic widget wake-ups.

Validation: assembleFossDebug and all 39 unit tests passed; APK signature verified.
SQLite checks cover migration, latest selection, deletion fallback and empty data.
Physical Lawnchair rendering and device behavior still need owner testing.

High-quality AAC-ELD reception remains the existing in-app recording experiment.
It is not exposed as a system microphone for cellular calls or other apps.
Android communication-device selection chooses existing system audio devices;
changing that route is not equivalent to injecting decoded microphone PCM.
A separate voice client could consume this stream with OpenAI's audio API, but
that would be a new integration, not an upgrade to the official ChatGPT app.
https://developer.android.com/reference/android/media/AudioManager
https://developers.openai.com/api/docs/guides/realtime-websocket

### Diagnose accepted exports missing from Samsung Health

Owner screenshot confirms daily sessions say Sent to Health Connect. This flag is
set only after insertRecords succeeds; it does not confirm Samsung imported them.
Daily and movement-triggered samples were incorrectly labelled actively recorded.
They now use autoRecorded; user-started workouts keep activelyRecorded. Stable
client IDs are preserved and version increases to 2 so resending corrects metadata
without creating another set of Health Connect records. This is a correctness fix,
not a proven explanation for Samsung's missing history.

Saved sessions now offer Check Health Connect: foreground read-back filtered to
LibrePods data origin and exact client IDs, paginated, comparing timestamps/BPM
against local samples. Android's service permits own-data reads with write access;
no additional read permissions are requested. Read failures are reported as
unverified, never as missing data. Resend is also available on previously exported
sessions. The UI explicitly distinguishes successful export from Samsung import.
Samsung ingestion and phone read-back still require owner testing.

Sources:
https://developer.android.com/health-and-fitness/health-connect/metadata
https://developer.samsung.com/health/blog/en/accessing-samsung-health-data-through-health-connect
https://android.googlesource.com/platform/packages/modules/HealthFitness/+/refs/heads/android17-release/service/java/com/android/server/healthconnect/HealthConnectServiceImpl.java
