# Desktop validation — 2026-10-03

Both desktop builds completed successfully. No AirPods hardware or target
Windows/Omarchy desktop was attached for acceptance testing.

| Check | Result | Evidence |
| --- | --- | --- |
| Arch Linux package, Qt app, CLI and installation layout | Passed | [Omarchy CI](https://github.com/jimmy8889/librepods/actions/runs/37112022607) at `fdc1ed3` |
| Qt packet parsing and real local IPC tests | Passed | CTest in Arch CI and locally on Debian 13 / Qt 6.8.2 |
| Windows battery and heart-rate protocol tests | Five passed | Hardware-independent Rust library tested in Omarchy CI |
| Windows daemon GNU cross-build | Passed | Local Rust build with checksum-verified FFmpeg |
| Windows daemon MSVC build | Passed | [Windows CI](https://github.com/jimmy8889/librepods/actions/runs/37112853928) at `717a8c7` |
| AAP, ACX mic and PortCls mic drivers | Compiled; catalogs generated | Windows CI |
| WinUI app, complete installation folder and MSI | Passed | Windows CI |
| Source import completeness | Passed | All upstream source files present, excluding documented prebuilt/fetched dependencies |
| Physical AirPods battery / noise modes / ear detection / reconnect | Not tested | Requires actual Windows and Omarchy sessions |
| Windows microphone and Pro 3 sensor | Not tested | Compilation and decoder fixtures are not hardware acceptance |

The Linux source tree at `717a8c7` is identical to the one built at `fdc1ed3`.
The Windows source is integrated from NTPods; see [provenance](../windows/PROVENANCE.md).

## Build artifacts

- [Omarchy package artifact](https://github.com/jimmy8889/librepods/actions/runs/37112022607/artifacts/11269384456):
  use `librepods-omarchy-0.1.0-1-x86_64.pkg.tar.zst`. The archive also contains
  the optional separate debug package.
- [Windows MSI artifact](https://github.com/jimmy8889/librepods/actions/runs/37112853928/artifacts/11271235738):
  extract `NTPods.msi` from the artifact ZIP.
- [Windows complete folder artifact](https://github.com/jimmy8889/librepods/actions/runs/37112853928/artifacts/11270931153):
  alternative ZIP installation folder.

GitHub Actions downloads require sign-in and expire with artifact retention.
These are development builds, not published stable releases. The Windows
drivers are not Microsoft signed. The user's explicit decision about Test Mode
and Secure Boot is still needed before installing those drivers on a PC.
No driver installation or boot-security changes were executed during this work.

SHA-256 of the extracted files:

```text
468ea130e732848c8c430e11e00867bd24961cc031e3a1c858c63afced3be3d9  librepods-omarchy-0.1.0-1-x86_64.pkg.tar.zst
ab22a421d589f8a7f7f024a4ba4f769f36dd9713ea1d1c04daede345cc3b0c4a  NTPods.msi
```

Install instructions and remaining feature gaps are in the
[support matrix](desktop-support.md), [Omarchy guide](../packaging/omarchy/README.md)
and [Windows guide](../windows/README.md).
