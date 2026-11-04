# Folio 1.23.0-preview.1

- Infinite-canvas first-fit uses bounds already calculated by the spatial index, avoiding a second full point-by-point bounds scan when opening large notebooks.
- Folio archive export writes its UTF-8 JSON through a buffered writer directly into the ZIP stream, avoiding the extra full-size UTF-8 byte array.
- Android build tools are pinned to 35.0.0 to match the API 35 build environment.

Build: release variant, signed with the existing Folio Local key. Tablet installation was intentionally not performed.

Validation: Java compilation and APK packaging succeeded. The Android release lint gate reports an existing `BlockedPrivateApi` issue in `shared/penbridge/SharedPenBridge.java` (reflective ioctl access); the prerelease was packaged with only `lintVitalRelease` skipped. APK signature matches the existing Folio signing certificate.
