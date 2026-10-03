# Windows source provenance

Imported from https://github.com/arctumn/ntpods at commit
`188e45df20259b616ce155be812086259d1a2488` on 2026-10-03.
The original GPL-3.0 license is retained in `LICENSE.ntpods`; existing copyright
notices and NTPods branding are retained. This is an integration in the user's
LibrePods fork, not an official NTPods or LibrePods Windows release.

The app, daemon, IPC library, driver sources, assets and installer sources are
included. Upstream prebuilt drivers and devcon.exe are deliberately excluded.
CI rebuilds drivers and obtains Microsoft's devcon from the installed WDK.
FFmpeg is fetched separately with upstream's pinned URL and SHA-256 verification.

Fork changes: artifact-only CI, source provenance, portable protocol test target,
parser validation, lockfiles and integration documentation. No upstream claim
of working hardware has been independently verified for this user's AirPods.

The imported install scripts create test certificates, install kernel drivers,
and register startup entries and helper tasks when explicitly run. They are not
executed by build or test commands. Do not use them until the user has chosen to
accept the Windows driver security requirements. Public production distribution
requires Microsoft-trusted driver signing.
