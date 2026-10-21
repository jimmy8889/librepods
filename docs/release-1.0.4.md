# Personal Android update to LibrePods 1.0.4

21 October 2026. Merged upstream stable tag `v1.0.4` (`89356c0`) into the personal fork, including the previously uncommitted v67 battery corrections. Android package remains `me.kavishdevar.librepods`; personal version code advances to 68.

Preserved the combined AirPods widget and Quick Settings controls, reconnect action, five-second connection popup, low-duty BLE scanning, battery unknown/estimated/last-reported semantics, finding tools, foreground microphone and heart-rate experiments, workouts and Health Connect code, FOSS feature access, and desktop Windows/Omarchy sources.

Upstream renamed conversation-awareness symbols. The fork retains its existing preference keys so installed user settings survive the update. Upstream legacy noise-control widgets and dialog remain removed in favor of the personal combined controls; their resource rename does not reintroduce them. The battery display retains the personal corrected reading semantics. Upstream device models, compatibility fixes, equalizer labels, translations and build versioning are merged.

The original working copy remains untouched. Integration branch: `codex/librepods-1.0.4-preserve-features`. New development-only rewrite/alpha releases are separate upstream work and are not the stable release merged here.

`assembleFossDebug`, all 57 `testFossDebugUnitTest` tests, and `lintFossDebug` passed. APK signature verification matches the installed v67 build. Google Drive upload and metadata readback verified the new APK in Android APKs; the prior LibrePods file was removed only after the replacement was verified. Phone installation/readback are recorded separately after completion. Hardware-specific AirPods functions still require actual device feedback; preserving source and passing unit tests alone do not establish real sensor or audio acceptance.
