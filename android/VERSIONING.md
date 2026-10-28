# Android app versions

The version is maintained in `version.properties` using semantic `VERSION_NAME`
and a monotonically increasing `VERSION_CODE`. Increase the code for every
APK distributed for installation; update the name according to the change:

- patch: fixes that preserve behavior (`0.2.1`)
- minor: new features (`0.3.0`)
- major: incompatible changes (`1.0.0`)

The initial numbered development build is `0.2.0` (code `2`); code `1` was the
unnumbered `0.1-test` build.

## 0.2.17 (code 19)

- Disable mid-simulation frame preemption for offline Android play. At low
  Polymer FPS, actor updates could draw inside the catch-up loop, continually
  adding clock debt while bypassing the guard at the start of the main loop.
  A read-only device capture on 0.2.16 showed about 19-21 simulation steps/sec
  and growing clock debt while the player stood still.
- Bound each offline catch-up burst to three simulation steps, with throttled
  logging when additional clock debt is discarded.
- Compute sprite transform matrices directly on Android instead of performing
  GL matrix-stack calls and reading the result back for every updated sprite.
- Gradle debug build completed; APK installed over the existing app via ADB.
  Live movement and comparative FPS still require an in-game run of this build.

## 0.2.18 (code 20)

- Experimental Android Polymer CPU visibility worker. A persistent background
  thread processes wall-facing calculations while the render thread submits
  the sky. Only copied coordinates are read; no GL or mutable world access.
- Reuse visibility results within a view. Previous-frame wall IDs supply only
  candidate jobs; coordinates/viewpoint are always current, and newly visited
  walls use the original exact check. Mirror/nested views remain serial.
- Worker-specific angle calculation avoids the shared libdivide cache used
  by gethiq16angle/scale. Small batches (<128 walls), one-core devices, and
  worker creation failures use the serial path.
- Console cvar r_pr_workers: 0 serial, 1 background (default). Aggregated timing
  logs every 300 main views show batch time, wait time, pass time, and job counts.
  FPS benefit has not yet been established in comparable phone captures.

## 0.2.19 (code 21)

- Batch Android Polymer screen text state across glyphs in each string. Keep
  the shader, vertex buffer and 2D matrices until the string finishes, then
  restore the caller state. Model-backed glyphs explicitly restore matrices
  before their existing model path runs.
- Cache palette lookup scale, shade offset and visibility uniforms per Polymer
  program; update them when their values change.
- Console cvar r_pr_textbatch (default 1) allows disabling text state batching.
- Build/install status and perceived menu smoothness are separate: a successful
  build does not establish a measured frame-rate improvement.

## 0.2.20 (code 22)

- Request a same-resolution high refresh display mode (up to 120 Hz), publish
  the actual display rate to the native frame limiter, and yield during menu
  waits instead of busy spinning. The OS may supply a lower refresh rate.
- Precache menu fonts/palettes and the basic UI shader during startup.
- Built, installed and archived. Comparative menu smoothness is not established.

## 0.2.21 (code 23)

- Correct Android Classic projection for the physical display aspect, including
  when the software framebuffer is smaller. Keep explicit aspect overrides.
- Built, installed and archived. In-game FOV has not been verified on the phone.

## 0.2.22 (code 24)

- Hide gameplay overlay in menus; route taps through SDL to the existing menu
  hitboxes, sliders, file lists and dialogs. System Back remains available.
  Blank-screen taps send Enter for intros/cinematics/respawn.
- Publish touch mode from the game thread. Transfer camera deltas and joystick
  targets atomically, applying sensitivity and smoothing on the game thread.
- Cache active Polymer lights in original priority/index order per view, shared
  across sprite/model light tests. Bound face-sprite light arrays. Empty light
  lists avoid slot scans; populated lists scan active slots once and sort IDs.
- Precompile two additional fullbright menu shader combinations.
- Gradle assembleDebug succeeded. 200 randomized light-list ordering comparisons
  matched the previous priority/index scan. No FPS increase is claimed yet.
- APK saved on PC and archived. Phone was taken by the user: this version has
  not been installed or tested on-device. Retain 0.2.21 as the previous build.

## 0.3 (code 27)

- Preserve the working Duke Android baseline, including touch menu routing and
  keeping the touch overlay attached when switching between menus and gameplay.
- APK and source snapshot saved separately in build-archive.

## 0.4 (code 31)

- Add Duke Nukem 3D / BloodCM presets with separate content-folder markers,
  game configuration and renderer preferences; retain shared Duke data selection.
- Add a styled preset dialog and extract BloodCM header artwork from user data.
- Launch the game in a separate process so preset changes restart native state.
- Resolve BloodCM GRP through the engine search path and select its CON/DEF files.
- Resolve GeneralUser through a local SoundFont alias in the BloodCM profile.
- Finish empty VOC blocks without invoking a zero-length mixer; validate Android
  mixer source spans before mixing WAV/VOC data.
- Gradle assembleDebug succeeded. BloodCM passed the former intro crash and
  reached a level on the connected phone; the user confirmed it works.
- Full system folder-picker flow and Duke preset regression were not rechecked
  in this final pass. Version 0.3 remains archived separately.

## 0.4.1 (code 32)

- Replace the directional cross with a radial analog movement stick. Apply the
  normalized axes at the existing fixed physics tick to retain the frame-stall
  movement correction. Keep the ordinary input path for recording/network modes.
- Keep camera control floating, display it only while held, and use the same
  translucent ring/thumb artwork for both sticks.
- Replace action labels with cached vector icons, subtle rims and held highlights.
- Reuse saved control positions. Add per-element size sliders in the layout editor,
  using the same vector renderer as gameplay. Reload layout on game resume.
- Clear movement axes on pointer release/cancel, input-mode change, resize and pause.
- Gradle assembleDebug succeeded (1m 19s). Installed on connected Xiaomi; screenshot
  confirms the level renders with the new overlay and active movement/look sticks.
- Device was being touched during observation; automated gameplay input was not
  injected. Direction, speed feel, simultaneous buttons and release behavior still
  need user feedback. No measured performance or quota savings are claimed.

## 0.4.2 (code 33)

- Preserve 0.4.1 APK and its source archive under build-archive/stable-0.4.1.
- Make movement stick floating on the left, matching the floating camera stick.
- Add opt-in Volume + to fire to the launcher. Combine physical/touch fire state
  and release on focus loss, pause and gameplay/menu transitions.
- Persist Android look/turn/move/strafe sliders in the per-preset config, immediately
  after menu changes and during ordinary configuration save.
- Extend CPU visibility preparation to an adaptive pair of helpers for >=1024 jobs,
  retaining serial/single-helper paths and exact same-frame fallback. No GL calls
  or mutable map reads run in workers. Add named worker threads and pair statistics.
- Cache additional per-program constant uniforms (fog/detail/specular/mirror/normal).
- Gradle build succeeded. Installed; the user reported normal operation.
- Set look=6.5/turn=7 through the menu; verified cfg write and reload after force-stop.
  The test values remain selected. Volume option enabled through the launcher;
  HID volume input exercised, but simultaneous user input prevents a clean ammo A/B.
- Archived APK and source snapshot separately. No controlled FPS gain established.

## 0.4.3 (code 34)

- Restrict extra additive world-surface light passes with conservative per-view
  projected light bounds, preserving original scissor state and near-eye fallback.
  Recursive mirror/shadow views bypass this optimization. The base pass is unchanged.
  r_pr_lightscissor allows disabling it for comparisons.
- Skip fragment reflection/specular math when finite light attenuation is zero.
- Report a missing CON quote once per ID instead of printing it every death frame.
- Gradle build succeeded (1m 13s); installed and reached E1L1 in Polymer.
- Actual light-bound header arithmetic check: 1897 restricted synthetic cases,
  78586 visible in-range samples, zero clipping violations; this is not a GPU test.
- Phone log over 300 frames: 8435 extra passes, 4243 restricted, average restricted
  rectangle 40.1% of viewport. This does not measure shaded pixels or FPS improvement.
  Screenshot and log confirm a rendered level; checked log has no fresh shader errors.
- Adaptive workers are active in mode 2; the sampled scene did not meet the paired
  helper threshold. No eight-core render scaling is claimed. Stable stays 0.4.1.

## 0.4.5 (code 36; includes experimental code 35 / 0.4.4)

- Profiled live Android Polymer using simpleperf: the HUD path repeatedly updated
  its shared streaming VBO through GL4ES. Driver worker threads already consumed
  CPU alongside SDL render and audio threads; this was not a single-threaded app.
- For flat Polymer HUD/menu sprites on Android without persistent mapping, submit
  tiny client vertex arrays instead of glBufferSubData per glyph. Restore the
  standard vertex pointers immediately. Retain old path with r_pr_hudstream 0.
  No vendored GL4ES source was modified.
- Add optional runtime-resolved ADPF render-work reporting, excluding present wait.
  This phone declines ADPF; it falls back to ordinary scheduling.
- Detect allowed CPUs and cpu_capacity from sysfs. Add opt-in Prefer performance
  cores to launcher, using high-capacity cores only for the render thread. Restore
  original mask on background/Classic mode. Default is off; topology is not hardcoded.
- Phone topology: six capacity-348 CPUs (max 2.0GHz), two capacity-1024 CPUs
  (max 2.2GHz). Affinity from inside the render thread succeeds (CPUs 6-7).
- External attempts to write app memory/change another process thread's affinity
  were denied and produced no valid benchmark. No system hardening was modified
  manually. Actual settings were changed through the normal in-game console via HID.
- Controlled stationary E1L1 scene, two six-second windows per HUD path, normal
  scheduler, frame counter read from the debug process:
    old VBO path: 94/6.001s=15.66 FPS; 96/6.009s=15.98 FPS
    new client path: 116/6.009s=19.30 FPS; 117/6.001s=19.50 FPS
  Approximately 22 percent improvement in this scene only. No all-level claim.
- Same scene, new HUD: normal affinity 19.32 FPS; performance-core-only affinity
  19.31 and 19.00 FPS. No benefit established, so it remains disabled by default.
- Final state verified: r_pr_hudstream=1, r_pr_bigcores=0, render mask restored
  to CPUs 0-7. Thermal status 0; no new entry in the checked crash buffer.
- Gradle build succeeded. One new UI string's encoding was corrected; a clean
  incremental build succeeded in 4 seconds. APK installed and scene rendered.
- Stable remains separately preserved 0.4.1. This build is an optimization candidate.

## 0.4.6 (code 37)
- Replace three launcher text switches with animated blue/grey graphical switches; retain separate cards and preferences. Visual check on phone passed.
- Add Android single-player perfmark/perfwarp console bookmarks storing coordinates, orientation and sector in private settings. Verified cinema E1L1 restored after fresh process. Benchmarks archived in benchmarks-0.4.6.
- Batch client-array pointer state across flat HUD text strings (r_pr_hudstatebatch). Outside text batches retains prior path.
- Experimental composed palette shader, flat-light math and legacy light-state cache are available for comparison but OFF by default: device measurements did not establish a gameplay gain. Do not claim FPS improvement for 0.4.6.
- Initialize four-component legacy light vectors, including model position, before GL calls.
- Gradle build succeeded; installed, rendered live Polymer E1L1 and measured 8-second stationary windows. See measurements.txt for results and excluded intro result.
- Stable 0.4.1 is untouched. BloodCM game data untouched.

## 0.4.7 (code 38)
- BloodCM-preset-only legacy compatibility; original CON, GRP and DEF data unchanged.
- Restore absolute negative-viewtype animation stride and missing-frame fallback from before r8787 (acc5da06c, March 2020). BloodCM zombie and rat actions use -5 for mirrored views while advancing frames forward; modern World Tour semantics advance backward. Ordinary Duke retains modern semantics.
- Restore pre-r6974 custom RPG spawn damage: assign PROJ_EXTRA only with PROJECTILE_RPG_IMPACT. BloodCM flare WORKSLIKE=6146 lacks this flag and implements attached flare damage/burning in EVENT_KILLIT/CON. Modern unconditional assignment introduced in r6978 (09034563b, September 2018) applies default damage 100, exceeding zombie health 80.
- Gradle assembleDebug succeeded, APK saved and installed after force-stop. BloodCM CON compile: 25 warnings, zero errors; E1L1 rendered. User is testing gameplay; zombie/rat appearance and flare damage behavior not yet confirmed. Do not mark fully verified until feedback.
- Separate preserved stable remains untouched.

## 0.4.8 diagnostic candidate (code 39)
- Application-level Java uncaught-exception capture, native-library load stages, bounded UID logcat files; TXT export works without an engine log.
- Export Android 11+ process-exit reasons and optional Android-provided trace as Base64 (may be native tombstone protobuf). System traces are not guaranteed; no claim to capture every crash.
- Avoid JNI calls after SDL reports broken native libraries.
- Emergency launch uses separate disposable CFG/settings, Classic, audio disabled, no autoload and no preferred-refresh/core tuning; ordinary config retained.
- Save-folder SAF permissions, per-preset snapshots in selected game folder; three completed generations, FileObserver on save/config writes and pause backup scheduling. Restore after choosing folder again. Game files unchanged; observer and restore round-trip not yet device-verified. Backup mapping is read from an atomic private file across processes.
- Classic aspect now reads actual softsurface destination dimensions. User reports proportions still wrong: unresolved, do not mark fixed.
- Gradle build passed; installed and normal launch confirmed, early startup files and bounded logcat created. User chose to perform gameplay tests; emergency launch and backup restore remain unverified. No known PC copy of deleted Android saves found in inspected workspace/archive paths.

## 0.4.9 (code 40)
- User-provided 0.4.8 extended report (Xiaomi stone Android 14): ordinary and emergency start SIGTRAP in loguru::init. Build ID dbc8d7f33fd87e4226d63c377ed72406da80944f matched unstripped binary. addr2line maps PC 0x1f2e24 to loguru.cpp:647, CHECK_EQ_F(argv[argc], nullptr); bridge PC 0x45f2b8 maps main.cpp:214.
- Append explicit null sentinel to bridge argument array; preserve argc excluding sentinel. Also terminate filtered SDL argv. Previously out-of-bounds vector storage made logging initialization nondeterministic.
- Extracted actual AndroidRunEngine helper compiled with stub engine checking ABI invariants. On-device standalone regression: 200 successful calls, alternating ordinary/emergency and bare/custom argument lists. No game data changed; no graphics test implied.
- Gradle build succeeded. APK saved separately. Full updated-app ordinary/emergency launches not yet rechecked on phone. Aspect issue remains open; storage restore round-trip remains unverified. Do not infer that all friends' crashes are fixed from this one device report.

## 0.4.10 (code 41)
- Android Classic framebuffer now matches native surface aspect, keeping approximately the requested pixel budget instead of stretching 1280x720 onto 2408x1080. Fix applies to 2D menus/title/weapon as well as world projection.
- Round buffer width to nearest 8 with minimum bounds; avoids resolution growth over repeated resume calculations. Preserve Classic buffer budget in SDL resume/resize paths.
- Numeric verification: 15 surface/budget combinations, residual pixel-aspect error below 0.2%, stable through 100 resume recalculations per combination.
- Gradle build succeeded, installed after force-stop, live E1L1 entered. Device log: requested 921600 pixels, surface 2408x1080, buffer 1432x642. User confirmed proportions normal.
- Saved APK separately; previous stable archives retained. Storage backup restore round-trip from 0.4.8 still requires verification; no new claim about all-device compatibility.

## 0.4.11 (code 42)
- Supplied 0.4.10 report: Xiaomi dew, Android 15, normal/emergency exits signaled status 6. Logcat explicitly records menus.cpp:1893 Menu_PopulateJoystick assertion name != nullptr. SDL selected uinput-fpc as controller; this is distinct from the prior argv crash.
- Menu no longer assumes every game controller has two trigger axes. Bound trigger count to clamp(numAxes-4,0,2), subtract hat rows in trigger index calculation. Preserve normal 6-axis controllers.
- Missing button/axis names use bounded fallback labels. Correct joyGetName count bounds from > to >=.
- Gradle succeeded. Actual updated Menu_PopulateJoystick extracted into an Android regression harness with a stub driver returning null names; 500 layouts executed on phone successfully. Includes zero-axis controller and raw joystick hat combinations. No assertion disabled globally, no game files changed.
- APK saved. Full-app launch on reporter's Xiaomi dew still requires confirmation; local game was not interrupted. 0.4.10 aspect fix retained.

## 0.4.12 (code 43)
- User reports volume-fire screenshot/power-button hold opens power menu or Gemini, rotates and drops the game. No crash stack for this specific event yet; targeted lifecycle/render fixes, not confirmed root-cause claim.
- Release input before SDL focus loss; clear captured volume/blank taps and native input. Cancelled key-up and Power/VolumeDown chords release physical fire. Force SDL orientation requests to sensor-landscape, defer focus-return refresh-rate request.
- Native focus changes now pause/resume gameplay while preserving manual pause ownership; foreground without focus remains paused.
- Android Classic obtains current window surface, defers portrait/background output, and checks destination dimensions before blitting. Reacquire software surface after mode changes even when binary has USE_OPENGL compiled but nogl is active.
- Resize no longer invokes GL viewport functions for Classic. Preserve 0.4.10 square-pixel frame sizing and 0.4.11 controller fix.
- Gradle succeeded, APK saved and installed after user saved progress. End-to-end power-menu/Gemini/volume-chord verification pending user test. No automated physical system chord reproduction claimed.
