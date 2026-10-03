# LibrePods on Omarchy

Build the native Qt application as an Arch package. It uses BlueZ for the
AirPods control connection and Omarchy's existing PipeWire PulseAudio server
for audio integration. No subscription or Android bridge is needed.

```bash
git clone --branch codex/desktop-windows-omarchy https://github.com/jimmy8889/librepods.git
cd librepods/packaging/omarchy
makepkg -si
librepods
```

Pair and connect the AirPods using Omarchy's Bluetooth settings before opening
LibrePods. Leave the existing PipeWire/WirePlumber audio stack in place.
Do not run the app as root. Dependencies are declared in the PKGBUILD;
`makepkg -s` asks pacman to install missing packages normally.

## Current Omarchy shell and startup

Current Omarchy uses a Quickshell bar and Lua configuration. The native Qt tray
icon appears in the shell's tray drawer. Open it to see batteries and controls;
you do not need Waybar. Add `o.launch_on_start("librepods --hide")` once to
`~/.config/hypr/autostart.lua`. Optional Lua bindings are in
[hyprland.lua](hyprland.lua). Use this syntax only if your installation has
`hyprland.lua`; the older configuration is described below.

These paths follow the [current Omarchy dotfiles manual](https://omarchy.org/manual/dotfiles/).

## Omarchy 3 / older Waybar setup

Keep `tray` in your Waybar modules so the LibrePods icon is visible. The Qt UI
also opens from the application launcher or `librepods`.

For installations using `hyprland.conf`, add `exec-once = librepods --hide` once to your own Hyprland autostart file,
or use the app's autostart setting. Choose one to avoid duplicate startup.
Optional Hyprland bindings are in [hyprland.conf](hyprland.conf).

Merge the module in [waybar.jsonc](waybar.jsonc) into your existing Waybar
configuration and add `custom/librepods` to `modules-right`. Restart Waybar using
Omarchy's usual configuration reload. Left click opens the app; right click
requests ANC; middle click requests transparency.

Battery/mode changes are pushed over a private per-user socket. The wrapper
uses a long-lived subscription; the five-second Waybar restart delay only
reconnects after the app stops or starts. It does not poll Bluetooth battery.
No files in your Omarchy configuration are overwritten by package installation.

```bash
librepods-ctl status                 # JSON, unavailable battery values are null
librepods-ctl watch                  # one JSON line per change, until disconnected
librepods-ctl noise:anc
librepods-ctl noise:transparency
librepods-ctl conversation:on
librepods-ctl one-bud-anc:on
```

Control responses mean a request was handed to the app; check the next device
notification to confirm it took effect. Disconnected controls exit with an error.

If ear gestures do not reach media players, see the existing
[WirePlumber AVRCP instructions](../../linux/README.md#pipewirewireplumber-recommended).
Use the user override there only if needed. Do not install a second audio server
or run `mpris-proxy` alongside WirePlumber.

Remove with `sudo pacman -R librepods-omarchy`, then remove any startup/Waybar
lines you added. Your pairing and app settings are retained.

See [desktop support status](../../docs/desktop-support.md) for features and
hardware verification limits.
