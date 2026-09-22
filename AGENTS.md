# APK delivery preference

The user wants only the newest LibrePods APK visible, to avoid version confusion.
For every new APK delivered:

1. Build and verify the new APK, then upload it to the existing Google Drive
   **Android APKs** folder (ID `1kzKCSTjj-ktUrvdHT3oLr9XW3FI5_VrW`).
2. Verify the uploaded file and destination before deleting the previous release.
3. Delete older LibrePods APKs from that Drive folder and from
   `/home/codex/apk-releases`, leaving only the newly delivered APK.
4. Verify that only the latest LibrePods APK remains and provide its link.

Do not delete unrelated apps, documents, source code, or signing keys. If the new
build or upload fails, retain the previous APK until a replacement is verified.
This is the user's standing instruction from 2026-09-22.
