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
