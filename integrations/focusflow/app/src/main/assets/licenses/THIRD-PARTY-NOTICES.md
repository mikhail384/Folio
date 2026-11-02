# FocusFlow third-party software

The APK includes the following unmodified libraries. Versions and SHA-256 hashes
are pinned in dependencies.lock.json in the source distribution.

* libadb-android 3.1.1, copyright Muntashir Al-Islam.
  Used under Apache License 2.0 (the upstream project offers a dual license).
  Source: https://github.com/MuntashirAkon/libadb-android/tree/3.1.1
* spake2-java / spake2-android 2.2.1, copyright Muntashir Al-Islam and
  the upstream SPAKE2 contributors. GNU Lesser General Public License 3.0.
  Corresponding source: https://github.com/MuntashirAkon/spake2-java/tree/2.2.1
  The LGPL and GPL license texts accompany this notice. FocusFlow does not modify
  this library. The complete FocusFlow source and build scripts allow rebuilding
  and relinking with a modified library; replace spake2.aar in .tools/libs and
  update its checksum in dependencies.lock.json. Reverse engineering for debugging
  modifications to this library is permitted.
* Conscrypt Android 2.5.3, copyright The Android Open Source Project.
  Apache License 2.0; additional BoringSSL notices in Conscrypt-NOTICE.txt.
  Source: https://github.com/google/conscrypt/tree/2.5.3
* Bouncy Castle bcprov-jdk15to18 1.81, copyright The Legion of the Bouncy Castle Inc.
  Bouncy Castle license (MIT style), reproduced in BouncyCastle.html.
  Source: https://github.com/bcgit/bc-java/tree/r1rv81
* AndroidX Annotation 1.3.0, copyright The Android Open Source Project.
  Apache License 2.0.
  Source: https://android.googlesource.com/platform/frameworks/support/+/androidx-annotation-release/annotation/

License texts are bundled in this directory inside the APK and source archive.
