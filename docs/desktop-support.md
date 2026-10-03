# Windows and Omarchy desktop support

See the [validated builds and download links](desktop-validation.md) for the
2026-10-03 verification record.

This fork has two native desktop paths:

- **Omarchy:** the existing Qt/BlueZ LibrePods app, packaged for Arch, with
  Hyprland startup/binding examples and a live Waybar battery/mode module.
- **Windows 10/11 x64:** the GPL NTPods WinUI app, Rust daemon and native
  L2CAP/microphone drivers, imported at a pinned source revision. NTPods branding
  is retained to acknowledge its author. See [provenance](../windows/PROVENANCE.md).

## Features present in the source

| Feature | Omarchy | Windows |
| --- | --- | --- |
| Left/right/case battery | Qt tray, window, CLI and Waybar | Native tray and window |
| Off / ANC / transparency / adaptive | Implemented | Implemented |
| Ear detection and media pause/resume | MPRIS/PipeWire integration | Windows media session integration |
| Conversational awareness | Implemented | Implemented |
| Reconnect after disconnection | Existing BlueZ reconnect logic | Daemon + BLE proximity watcher |
| Rename / one-earbud ANC | Implemented | Implemented |
| Hearing aid settings | Existing separate script; needs audiogram and Apple identity | Experimental UI |
| Personalised volume and further settings | Limited in legacy Linux UI | Imported Windows controls |
| High resolution microphone | Not implemented | Imported decoder and virtual capture driver |
| AirPods Pro 3 heart rate | Not implemented | Imported sensor decoder and SQLite history |
| Head gestures | Not implemented | Not implemented |
| Apple Find My network / UWB precision finding | Not implemented | Not implemented |

These are source capabilities, not a claim of tested behavior on the user's
AirPods Pro 3. Linux is based on the Qt implementation already in this fork,
not the newer upstream Rust rewrite. Platform differences remain explicit.

## Install and build

Omarchy: [package instructions](../packaging/omarchy/README.md).

Windows: [application and driver instructions](../windows/README.md).
The `Build Windows desktop` workflow compiles the drivers, daemon, WinUI app
and MSI, and uploads artifacts without automatically publishing releases.
The MSI/ZIP is a **development build**: driver packages are not Microsoft signed.
The user requires Secure Boot to remain enabled. These artifacts do not meet
that requirement and should not be installed on their PC. Microsoft signing
is required for the kernel drivers; self-signing does not provide compatibility.
Build commands do not change those settings or install anything on a device.
No remote PC or work iPhone is modified.

LibrePods on Omarchy uses userspace Qt/BlueZ and does not require disabling
Secure Boot. The operating system's boot chain is separate: the current
[Omarchy installer guide](https://omarchy.org/manual/getting-started/) assumes
Secure Boot is disabled. An existing Omarchy installation needs a correctly
signed/trusted boot chain before enabling Secure Boot; do not infer that from
this app package building successfully. No firmware keys or boot files are
changed by this fork.

Pure protocol tests can run on Linux:

```bash
cargo test --manifest-path windows/daemon/Cargo.toml --lib --locked
cmake -S linux -B build -G Ninja -DBUILD_TESTING=ON
cmake --build build
ctest --test-dir build --output-on-failure
```

The Omarchy workflow builds and checks the package inside Arch Linux. The
Windows workflow is needed for WinUI/WDK build validation. A cross-compiled
Windows daemon by itself does not validate the app, drivers or installer.

## Hardware acceptance still required

For each OS, connect the actual AirPods and verify separate battery values,
each supported noise mode, conversation awareness, ear detection with a playing
media app, renamed-device discovery, disconnect/reconnect, sleep/wake and tray
startup. On Omarchy also check Waybar and media behavior under Hyprland.
On Windows verify driver loading first, then sensor and microphone behavior
separately. Capture real BPM only when the sensor returns validated readings;
empty or low-confidence data is not a successful heart-rate test.

Do not mark unimplemented features or vendor hardware claims as accepted based
on compilation, generated packets or simulated notifications.
