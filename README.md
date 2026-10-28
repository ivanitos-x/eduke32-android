# EDuke32 Android

An unofficial Android port of [EDuke32](https://www.eduke32.com/), based on the
[upstream source](https://voidpoint.io/terminx/eduke32). This repository keeps the
upstream history and adds the Android launcher, touch controls and renderer integration.

**Work in progress.** Current development version: **0.4.12**. Tested primarily on
one Xiaomi/POCO device; compatibility with every phone is not established.

## Features

- Classic software renderer with screen-aspect correction.
- Experimental Polymer renderer through GL4ES and OpenGL ES.
- Floating movement/look sticks, adjustable controls and optional Volume+ firing.
- Duke game-folder selection and a BloodCM preset with engine-side legacy compatibility.
- MIDI playback using TinySoundFont and GeneralUser GS SoftSynth v1.44.
- Emergency launch with a separate configuration, Classic rendering and audio disabled.
- Shareable diagnostics: engine log, Java errors, accessible Android logcat and process-exit information.
- Save/settings snapshots in a user-selected folder (restore verification is still pending).

## Playing

Requires an **ARM64 Android device, Android 7.0 / API 24 or newer**. The current APK
contains `arm64-v8a` native libraries only. Polymer support depends on the GPU/driver.

Supply your own legally obtained Duke Nukem 3D game data. No Duke GRP files,
BloodCM/AMC game distributions, saves or personal settings are included.

1. Install the APK as an update when possible; uninstalling deletes app-private data.
2. Select a GRP or a game folder in the launcher. BloodCM requires its own supplied files.
3. Start with Classic. Enable Polymer separately to try the experimental renderer.
4. Select **Saves & settings folder** to enable persistent snapshots before reinstalling.
   The app stores three completed ZIP generations in an `eduke32-android-*` subfolder.
   Select that folder again after reinstalling to restore. Verify a backup exists first.

If launch fails, try **Emergency launch**, reopen the launcher and use **Send log**.
Review the report before sharing: it contains device information, paths and app logs.

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
Keep your signing key private and backed up when distributing builds.

Version numbers are in `android/version.properties`; change history and validation
notes are in [android/VERSIONING.md](android/VERSIONING.md).

## Known limitations

- Polymer is experimental; performance, lighting and lifecycle recovery need more device testing.
- Power-menu / assistant / screenshot focus handling in 0.4.12 awaits end-to-end confirmation.
- Persistent save-folder restore needs a verified reinstall/restore round trip.
- AMC TC support is not provided. BloodCM gameplay fixes need broader testing.
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
