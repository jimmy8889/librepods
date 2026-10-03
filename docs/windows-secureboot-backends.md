# Windows backend choices with Secure Boot enabled

The owner's PC already boots both Windows and Omarchy with Secure Boot enabled.
Keep that working boot chain. Android wireless ADB/device testing is deferred
until the owner is ready; it is not a prerequisite for this assessment.

The owner requires **built-in Bluetooth only**, reports a MediaTek Wi-Fi 7
module in the ProArt P16, and is open to any implementation that works well in
Windows. The exact Bluetooth chipset/USB function is not yet read back from the
PC. WSL was an idea, not a requirement. A dedicated external adapter is ruled out.

The recommended route is the native Windows backend with Microsoft-signed
drivers: it retains Windows' Bluetooth/audio stack, its normal headphone and
microphone devices, and the existing tray/UI design. The current driver builds
are development signed. Access to the Hardware Developer program and an EV
certificate is an external prerequisite; the published build alone cannot
remove it. Signing does not replace actual hardware acceptance testing.

The earlier Microsoft-signing requirement applies to our current NTPods kernel
drivers. A different Bluetooth transport could avoid those custom drivers.
No alternative below has yet been accepted on the owner's AirPods hardware.

| Backend | Secure Boot route | Main integration limitation | Current state |
| --- | --- | --- | --- |
| Native NTPods using Windows Bluetooth | Microsoft-signed AAP and microphone drivers | Requires signing/submission and Windows hardware acceptance | Builds available, development drivers not suitable for this PC |
| Linux LibrePods in WSL 2 | Official usbipd-win plus Linux Bluetooth stack in WSL | Adapter is exclusive to WSL; Windows audio and microphone need a separate bridge/integration | Technically plausible, not an implemented complete Windows solution |
| Native userspace Bumble with a dedicated USB adapter | Microsoft's WinUSB driver plus userspace Bluetooth | Extra hardware; adapter leaves Windows Bluetooth stack | Ruled out by the owner |
| Native Omarchy | Existing signed/trusted OS boot chain plus Qt/BlueZ app | Linux-specific media integration; sensor/microphone/UWB feature differences remain | Package builds and software tests passed; user's AirPods acceptance pending |

## WSL feasibility

WSL does not transparently forward Windows Bluetooth sockets or our native
NTPods driver. The **Linux implementation** would run inside WSL, with a physical
USB Bluetooth radio passed through using usbipd-win, Linux Bluetooth modules
and firmware, BlueZ, D-Bus and the required Qt/media libraries.

Microsoft's current 6.18 WSL kernel source config enables Bluetooth as modules
(`CONFIG_BT=m`, `CONFIG_BT_HCIBTUSB=m`) and BR/EDR and LE. That is evidence about
the source configuration, not proof that the owner's installed WSL kernel has
the needed modules/firmware or that a particular radio works. Inspect those
before proposing a custom kernel.

While attached to WSL, the radio cannot be used by Windows. Passing the PC's
only radio could also remove Windows Bluetooth keyboards/mice and its AirPods
audio endpoint. A dedicated adapter avoids taking other devices' radio, but
does **not** establish that the AirPods can keep audio on one host/controller
while accepting AAP controls from another. Linux connection setup in this fork
also activates its A2DP profile; it needs adjustment/testing before a purported
controls-only helper is used alongside Windows audio.

A usable single-adapter design would need Windows audio routed into Linux's
AirPods output, a microphone return path, Windows media/ear-detection handling,
native UI/IPC integration, reconnect and sleep/wake recovery. Those bridges are
not supplied by running the Linux GUI under WSLg. Extra latency and normal
Windows sound-device selection must be measured, not assumed.

## Native USB alternative

This separate-adapter route was investigated before the owner clarified the
built-in-only requirement; it is retained as research context and is not the
selected implementation.

Google Bumble supports Windows USB HCI using WinUSB. This places the Bluetooth
host stack and L2CAP in userspace and could provide AAP without our custom
kernel driver. It requires a compatible dedicated USB radio bound to WinUSB;
the adapter then appears as a USB device rather than a Windows Bluetooth radio.

The built-in Windows Bluetooth adapter should remain untouched. Do not switch
its driver, copy Windows Bluetooth pairing secrets, or claim a second adapter
will preserve audio automatically. First establish a real AAP session using the
dedicated adapter and test coexistence with Windows audio. If coexistence is
not possible, this design also needs its own audio path. A WinUSB transport
alone does not expose the high-quality microphone to all Windows apps.

For seamless use of the **existing Windows Bluetooth/audio stack**, Microsoft
signing of the current native drivers remains the direct route. No Hardware Dev
Center account/certificate or signed driver release is available in this task.

## Read-only report for the PC

The [diagnostic script](../windows/tools/diagnose-backends.ps1) reports Secure
Boot, active boot test-signing flags, presence of WSL/usbipd commands, and USB
Bluetooth radio VID/PID without serial numbers. It neither changes settings
nor moves an adapter. It was written in the Linux workspace and has not been
executed on the Windows PC.

Run from a PowerShell opened at the repository root:

```powershell
powershell -NoProfile -File .\windows\tools\diagnose-backends.ps1
```

Next, verify the actual built-in radio with the read-only report. For the native
route, prepare and obtain Microsoft-signed packages, update the release
installer to preserve those signatures instead of test-signing them again,
then verify driver loading, controls, media, mic, reconnection and sleep/wake
on this PC with Secure Boot still enabled. No signature/submission account has
been provisioned or used here.

WSL remains a fallback if a full audio bridge is acceptable. A prototype by
[Caroxos](https://github.com/Caroxos/LDAC-Windows-WSL2) uses Windows WASAPI loopback
capture, sends audio to a Linux receiver, and owns the Bluetooth radio via USB/IP.
Its source is evidence for that architecture, not AirPods support or measured
latency/reliability on this PC. AirPods do not gain LDAC from this design; their
negotiated codec must be used. Its prebuilt binaries and installer are not used
here, and it does not establish a complete microphone-return solution.

Sources checked 2026-10-03:

- [Microsoft USB device forwarding for WSL](https://learn.microsoft.com/en-us/windows/wsl/connect-usb)
- [Microsoft WSL kernel configuration](https://github.com/microsoft/WSL2-Linux-Kernel/blob/linux-msft-wsl-6.18.y/arch/x86/configs/config-wsl)
- [Windows Bluetooth driver stack and L2CAP interfaces](https://learn.microsoft.com/en-us/windows-hardware/drivers/bluetooth/bluetooth-driver-stack)
- [Google Bumble Windows USB HCI support](https://google.github.io/bumble/platforms/windows.html)
- [Microsoft driver signing requirements](https://learn.microsoft.com/en-us/windows-hardware/drivers/dashboard/code-signing-reqs)
- [Linux MediaTek USB Bluetooth support](https://github.com/torvalds/linux/blob/master/drivers/bluetooth/btusb.c)
