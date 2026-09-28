# Samsung Health and Apple interoperability investigation

Checked 2026-09-28. No new interoperability feature is enabled by this document.

## Samsung Health: export now verified on the owner's phone

The owner's screenshot shows a daily session at 28 September 01:19 with one
96 BPM sample. The app's foreground read-back verified that sample in Health
Connect. This is stronger evidence than insertRecords success. It does not prove
that Samsung Health imported or displayed it. Do not relabel it as Samsung sync
success, repeat write-permission instructions as a diagnosis, or invent exercise
sessions to make passive readings appear.

Next device evidence: Samsung Health version, whether its heart-rate history has
that exact sample/time, and its Health Connect recent access. The phone is not
currently available to ADB. tools/device-checks/health-and-uwb.sh collects versions,
relevant permission results and firmware-advertised UWB support when connected.
It makes no device changes and does not dump health records or accounts.

Samsung's direct Data SDK supports heart-rate reading/writing, but write access
requires partner approval/access credentials even in its developer mode. It is
not a drop-in unrestricted replacement for Health Connect. No SDK integration,
partner application or account login was performed.

Sources:
https://developer.samsung.com/health/data/overview.html
https://developer.samsung.com/health/data/guide/developer-mode.html
https://developer.samsung.com/health/blog/en/accessing-samsung-health-data-through-health-connect

## AirPods/AirTags: network finding and UWB are separate

Android UWB requires hardware support plus a compatible peer and exchange of
session configuration through an out-of-band channel. Detecting a tag over BLE
or having UWB in the phone is insufficient to range to an Apple accessory.
Apple's Nearby Interaction accessory protocol is not proof of access to the
AirTag/Find My owner protocol. No verified Android precision-finding path for
this user's tags or AirPods Pro 3 case has been established in this investigation.

OpenTagViewer is a candidate for retrieving the owner's Find My location reports
on Android through its account/key import workflow. Its documented AirPods support
is lightly tested. This is network location, not guaranteed live proximity, UWB
ranging or authenticated owner-presence support. Its repository still lists
owner-connected ringing and Android registration as hardware research questions.
No credentials or tag keys have been requested, transferred or stored here.

Apple documents movement-triggered sounds after separation from the owner.
Viewing network reports does not demonstrate the owner connection needed to
change this state. First establish the user's existing paired Apple device and
account setup; do not promise that an Android map viewer suppresses those sounds.

Sources:
https://developer.android.com/develop/connectivity/uwb
https://developer.apple.com/documentation/nearbyinteraction
https://github.com/parawanderer/OpenTagViewer
https://github.com/parawanderer/OpenTagViewer/issues/166
https://support.apple.com/en-gb/119874

## Original Apple Watch SE

WatchWitch is an actual Android interoperability research implementation. Its
reported test setup is Series 5/watchOS 7.3.3 with iPhone 8/iOS 14.8; setup needs
a jailbroken iPhone and rooted Android for the tunnel. It receives health data
and offers limited notification functionality. Its README reports watchOS 10's
implicit-IV ESP mode as a tunnel blocker. SE support cannot be inferred solely
from age or similar hardware; obtain exact watchOS and paired-iPhone versions.

Peepo is kernel read/write and process-inspection research, not an Android sync
solution. Its author lists SE first-generation support as untested for specific
10.6.x builds and warns of panics on incompatible builds. Do not deploy it or
update the Watch merely because an exploit exists. No jailbreak was attempted.

A companion watch/iPhone app relaying user-authorized HealthKit data is another
route to assess if the user retains an iPhone. That is health-data bridging, not
a full Android replacement for Apple's Watch pairing, calls or notifications.
Mac/Xcode signing and physical-device testing would be required.

Sources:
https://github.com/seemoo-lab/watchwitch
https://github.com/datalocaltmp/Peepo
https://support.apple.com/en-mide/118490

## Pending user/device information

- Samsung Health version and visible heart-rate history for a verified sample.
- Exact Watch model/watchOS version; paired iPhone availability and iOS version.
- Desired Watch features: health data, notifications/calls, or both.
- Samsung root status and actual UWB feature report before proposing device work.
- Existing AirTag ownership/pairing setup before choosing an authenticated client.

## Owner clarification and next architecture

Owner confirms original Apple Watch SE on watchOS 10.6, wants health data and
Android notifications, and retains its paired iPhone. Upstream WatchWitch's
current README still lists the watchOS 10 tunnel limitation; repository HEAD
checked was b597026a75c1990c95f4002e0c10acd0dfbe8412. Do not present it as ready
for this Watch or suggest an update/downgrade as a solution.

Preferred candidate: retain Apple pairing. A companion iPhone app can read
user-authorized HealthKit changes and relay heart rate/workouts to Android for
Health Connect import. Use stable source IDs, preserve Watch device provenance,
handle corrections/deletions and prevent Apple↔Android sync loops. Background
HealthKit delivery is scheduled by iOS and cannot be promised as instantaneous.

Notification candidate: Android NotificationListenerService with explicit user
access and app selection -> authenticated relay -> companion Apple app/APNs ->
Watch alerts. These are bridge notifications, not native Android app pairing;
replies/dismissal require separate implementations. Avoid forwarding sensitive
notification content by default. A Watch away from the iPhone still needs its
own Wi-Fi/cellular connection for remote delivery. Never promise that Android
Bluetooth pairing alone supplies that connection. Signing, push credentials,
networking and physical Apple-device testing are still required.

Pending: iPhone model/iOS, Mac availability, and whether the iPhone can stay online.
No relay server has been created and no notification/health data has been sent.

https://developer.apple.com/documentation/healthkit/executing-observer-queries
https://developer.apple.com/documentation/watchos-apps/taking-advantage-of-notification-forwarding
https://developer.apple.com/documentation/watchos-apps/enabling-and-receiving-notifications
https://support.apple.com/en-us/108300

Owner reports Samsung Health 7; imported readings are not visibly apparent.
Samsung publishes a specific 7.0 sync troubleshooting page pointing to its own
Settings > Privacy processing consents. These are separate from Health Connect
app permissions. Check health-and-wellness processing status with the owner;
this is a candidate cause, not proof. Do not recommend enabling unrelated AI
training, location, medication or cycle-tracking consents for this task. Inspect
Heart rate > View all for the known sample before changing or resending data.
https://www.samsung.com/us/support/troubleshoot/TSG10013213/
https://www.samsung.com/hk_en/support/mobile-devices/measure-your-ecg-with-the-galaxy-watch-series/

## Revised constraint: no custom iPhone software

Owner can keep the paired iPhone online but cannot install custom software on it.
They have a Mac they can use freely. This supersedes the proposed iPhone companion
architecture. Do not build or recommend an iPhone companion or jailbreak as the
current route.

Candidate: an independent watchOS 10-compatible app installed on the Watch only,
using HealthKit permissions on the Watch and network transport to an authenticated
relay/Android receiver. Apple documents watch-only apps with no paired-iPhone app
installation. Notifications could arrive as this app's alerts through APNs; this
requires signing/push configuration and a network-connected Watch, and is not
native mirroring of every Android app's full capabilities. Health export scope
is data accessible on the Watch, not a promise of the entire iPhone Health archive.
Mac can build/sign and host a relay but cannot directly read/write the HealthKit
store via macOS HealthKit. Merely keeping iPhone/Mac online does not implement sync.

Installation feasibility remains open: Apple's Xcode device-pairing instructions
require Developer Mode on the paired iPhone and Watch. Asked whether those settings
changes are allowed (without app installation), and for macOS/Xcode versions.
Current Codex host is Linux and has no xcodebuild or access to the user's Mac.
No Watch app has been compiled, signed, installed or claimed functional.

Screenshot 4005 shows Samsung Health's main Hours heart-rate chart empty and an
'Other data from this period' section at the bottom. Check the content below this
section before concluding Samsung imported nothing; third-party display placement
is a hypothesis, not verified behaviour. LibrePods' sample read-back remains
verified. No additional export-format changes or record deletion are justified.

https://developer.apple.com/documentation/watchos-apps/creating-independent-watchos-apps
https://developer.apple.com/documentation/xcode/pairing-your-devices-with-your-mac
https://developer.apple.com/documentation/watchos-apps/enabling-and-receiving-notifications
https://developer.apple.com/documentation/healthkit/hkhealthstore/ishealthdataavailable()
