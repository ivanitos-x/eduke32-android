# EDuke32 Android

An unofficial Android port of [EDuke32](https://www.eduke32.com/), based on the
[upstream source](https://voidpoint.io/terminx/eduke32). This repository keeps the
upstream history and adds the Android launcher, touch controls and renderer integration.

**Current release 0.7 (71).**
Testing has focused on one Xiaomi/POCO device.

[Download the Android APK](releases/EDuke32-Android.apk)

## Features

- Classic 8-bit software rendering, Polymost in 16-bit or 32-bit color, and
  experimental Polymer in 32-bit color with dynamic lighting.
- Floating movement and look sticks, adjustable button layouts, custom bindings
  and optional Volume+ firing.
- Textured automap with one-finger panning and two-finger zoom and rotation.
- Duke Nukem 3D and BloodCM presets, plus custom presets for game folders and
  PC launch scripts.
- MIDI playback using TinySoundFont and GeneralUser GS SoftSynth v1.44.
- Shareable engine logs, Java errors, available Android logs and process-exit information.

## Playing

Requires an **ARM64 Android device, Android 7.0 / API 24 or newer**. The current APK
contains `arm64-v8a` native libraries only. Polymer support depends on the GPU/driver.

Supply your own legally obtained game data. The APK contains the port and its
runtime dependencies.

1. Install the APK as an update when possible; uninstalling deletes app-private data.
2. Choose a preset and select the game's directory under **Game folder & mods**.
   Grant file access when Android prompts. BloodCM requires `BloodCM.grp` and
   the `blcons` directory.
3. If **PC launch script** appears, select the script you want to use. See
   [supported script syntax](android/PC_LAUNCH.md).
4. Select a renderer and tap **Launch**. In game, open **Options → Touch Setup →
   Arrange controls** to move or resize buttons and change custom bindings.

With a direct working folder, the engine loads resources on demand and writes
saves and settings to that folder using its usual PC paths.

If launch fails or the game crashes, reopen the launcher and use **Send log**.
Review the report before sharing because it contains device information, paths
and app logs.

## Building

The Android project is in `android/`. SDL and GL4ES sources are included; no submodule
initialization is required.

Install Android Studio / Android SDK and configure:

- Android Studio JBR (the local build uses JBR 25.0.3)
- Android SDK Platform 37 and suitable build tools
- NDK `28.2.13676358`
- CMake `3.22.1`

Set `ANDROID_HOME` to your SDK or create an untracked `android/local.properties`:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

Build on Windows:

```powershell
.\android\gradlew.bat -p android assembleDebug
```

On Linux/macOS:

```sh
./android/gradlew -p android assembleDebug
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk`.
The wrapper downloads its configured Gradle distribution. Debug builds use the
machine's debug signing key; a different signing key cannot update an existing APK.
For a signed release, configure `android/signing.properties` locally with
`storeFile`, `storePassword`, `keyAlias`, and `keyPassword`, then run
`assembleRelease` through the same Gradle wrapper. With local signing
configured, debug and release share the same certificate. Keep the keystore
and its credentials private and backed up.

Version numbers are in `android/version.properties`; change history and validation
notes are in [android/VERSIONING.md](android/VERSIONING.md).

## Known limitations


- Polymer is experimental; performance, lighting and lifecycle recovery need more device testing.
- Persistent save-folder restore needs a verified reinstall/restore round trip.
- BloodCM gameplay fixes need broader testing.
- Native crash traces depend on what Android makes available; the report cannot capture every failure.

## Credits and licenses

EDuke32 and its contributors provide the engine. See original copyright/license
notices throughout `source/` and `package/common/buildlic.txt`; this port does not
replace or relicense those notices. Dependencies retain their own licenses:

- SDL 2.32.10: `android/SDL/LICENSE.txt`
- GL4ES: `android/third_party/gl4es/LICENSE`
- GeneralUser GS: `android/app/src/main/assets/GeneralUser GS LICENSE.txt`
- Other bundled engine libraries: their existing notices in `source/`

Duke Nukem 3D, Blood and related names/artwork belong to their respective owners.
This is a community port, not an official release from those rights holders.
