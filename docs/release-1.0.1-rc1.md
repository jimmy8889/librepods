# Custom LibrePods update: v1.0.1-rc1

Integrated the upstream release tag `v1.0.1-rc1`, commit
`efccc4499b2c1fd2ac243e975ae1f42d8e828ffd`, into the custom fork.

Upstream adds AirPods 5 model definitions and prevents unwanted automatic
connections on service start. This fork also registers both new model classes
for actual model-number lookup, with regression coverage for the new models,
existing Pro 3 models, unknown numbers and duplicate entries.

Android version code is 66, version name `1.0.1-rc1-debug`, using the existing
signing certificate for an in-place update. Free feature access, widgets,
Quick Settings controls, notification suppression, workout/Health Connect
integration, experimental audio tools and the existing settings are retained.
AirPods case UWB precision finding remains unavailable; the lab tools do not
establish a supported case finding feature.

Omarchy package version is `1.0.1rc1-1`. Upstream's Qt Linux sources did not
change in this release; the rebuilt package retains our BlueZ controls, CLI,
tray, optional Waybar module and Hyprland integration. No boot-chain or Secure
Boot changes are required by the application package. Installation and real
AirPods behavior on the user's Omarchy PC remain separate acceptance checks.

## Verification and delivery — 2026-10-10

Implementation commit: `e6485a6a83b1918b5718aa4f1b176dac3b03a2b6` on
`codex/update-librepods-1.0.1-rc1`. This contains both the upstream release and
the preceding custom device-investigation branch as ancestors.

- Android `assembleFossDebug`, 46 unit tests and `lintFossDebug` passed.
  Lint: zero errors, 272 warnings. Certificate SHA-256 matches the preceding
  installed custom APK: `0ec4fbb9f9f6611be2a7494ce11016911a8b21b23b6b6ccd3d18a123f96e9ab7`.
- In-place ADB installation on the user's Samsung SM-F976B succeeded.
  Package readback reports version 66 / `1.0.1-rc1-debug`. MainActivity opened;
  its live UI hierarchy contained LibrePods listening-mode controls, and the
  app process remained running. This is a launch check, not a new physical
  acceptance test of every AirPods feature.
- The APK manifest continues to omit `POST_NOTIFICATIONS`.
- [Omarchy CI](https://github.com/jimmy8889/librepods/actions/runs/38019546620)
  passed on the implementation commit: both desktop CTest checks and all five
  Rust protocol tests passed. The x86_64 package was downloaded and its package
  metadata, CLI, desktop launcher and Omarchy integration files verified.
- [APK backup](https://drive.google.com/file/d/19ISmq2jhMwgvhCTn48h33Ry9qnttDLNc/view?usp=drivesdk)
  was verified in the existing Android APKs folder before removing version 65.
- [Omarchy package download](https://github.com/jimmy8889/librepods/actions/runs/38019546620/artifacts/11657935568)
  contains `librepods-omarchy-1.0.1rc1-1-x86_64.pkg.tar.zst` and an optional debug
  package. GitHub sign-in is required and Actions retention applies.

SHA-256:

```text
d7fbf1a324518cfb023840b66313a0de811acd971a8d739d80921664c9f99758  LibrePods-1.0.1-rc1-jimmy-e6485a6-v66-debug.apk
97dd4e707aa9688e78a75b7bf3b83f76b98614351d1200f6fc02155309cf1cb0  librepods-omarchy-1.0.1rc1-1-x86_64.pkg.tar.zst
```

Extract the Omarchy download, then install the main package:

```bash
sudo pacman -U librepods-omarchy-1.0.1rc1-1-x86_64.pkg.tar.zst
librepods
```

No installation on the user's Omarchy PC was performed. No firmware keys,
boot-security settings, paired devices or app data were reset.
