# SpatialSurvivor Agent Guide

## Current state

SpatialSurvivor is a PICO Spatial SDK 0.13.3 Android/Kotlin project generated
from the official `stage` template. It opens `SpatialSurvivorStage` as a Mixed
MR Stage in Full Space and contains a working room-scale player-core vertical slice:
HMD-synchronized movement, a two-meter attack halo, nearest-target automatic
fire, pooled straight-line energy projectiles, contact damage and game over.
Its monster vertical slice consumes Scene Mesh vertices/semantics to create a
world-space navigation grid, spawns three boundary archetypes and ceiling
droppers, follows bounded A* waypoints, and runs death/drop lifecycles.
Defeated monsters now drop pooled floating EXP crystals that attract within the
player's dynamic pickup radius and award type-specific EXP. Experience-full opens a combat-pausing three-choice upgrade modal: it is placed
once in world space 1.0 m ahead of the HMD at eye height facing the player,
then stays fixed (no head follow). Scene Mesh clearance retreats the panel
toward the open-room center when the ideal point is blocked or within 0.3 m of
obstacles. A pooled gold ring expands around the player and the view HUD shows
the applied upgrade for 1.5 seconds.
Runs now advance through two-minute waves with cumulative health, active-count,
and speed pressure; Runner, Armored, and Ceiling Dropper archetypes unlock over
the first four waves. At 10:00 a pooled final Boss clears normal monsters,
follows the Scene Mesh navigation grid, and uses a telegraphed radial attack.
Boss defeat or player death pauses combat and opens a player-forward result
panel with run statistics, upgrade history on victory, crystal rewards, and
restart / permanent-progression / main-menu actions. The permanent-progression
AttachmentPanel uses the same one-shot 1.0 m world lock as upgrade and
settlement (no head follow); closing clears the lock so the next open
recomputes from the current HMD pose.
The previous wrist-bound status card has been replaced by one non-interactive
player-view HUD attachment: a top health bar/value and bottom EXP bar/level,
countdown, and active weapon-skill icons. It is placed 1.2 meters ahead and 15
degrees below the HMD gaze, uses a yaw-only billboard, smooths to the latest
head pose over 0.1 seconds, and receives a bounded recenter boost after large
turns. Health damage drives a 300 ms red flash in that unified panel. It stays
visible without hand tracking, and dims to 50% while the settlement overlay is
active. Health damage increments a presentation sequence that drives
a short red flash without entering the fixed-step gameplay state.
Spatial robustness now rejects every monster spawn outside an open Scene
Mesh-derived navigation cell instead of clamping invalid world positions onto
the grid edge. EXP crystals expose a lightweight 0.10-meter gameplay pickup
sphere but deliberately have no rigid body or Scene Mesh collider, so furniture
cannot push or block them. Successful hits apply a pooled scale pulse and a
capped 50-millisecond gameplay hit-stop. Normal-monster chase work runs at 90,
45, or 22.5 Hz beyond the 6- and 10-meter distance bands while accumulated
delta preserves average movement speed. HMD sample freshness gates gameplay:
a 0.35-second stale sample pauses time and motion, and 0.15 seconds of stable
samples resumes them.
All torus-based ground indicators now use the SDK primitive's native horizontal
XZ orientation, including the player attack range, poison aura, and Boss area
telegraph. Every chasing monster archetype uses a shared aggressive attack rule:
it sprints at 1.35x speed inside 2.5 meters and attacks within collider contact
plus 0.18 meters of melee reach; ceiling droppers cannot attack before landing.

## Non-negotiable project rules

- All 2D UI must use `com.pico.spatial.ui.*`, be wrapped in `PicoTheme`, and
  must not use Material or Material3.
- Keep gameplay 3D objects in PICO Spatial ECS `Entity` hierarchies and use
  Stage/world coordinates in meters.
- Physical HMD movement is the player movement source; do not add a virtual
  locomotion joystick.
- Hand tracking is primary input and controller-ray input is the fallback.
- Keep deterministic gameplay on the fixed-step loop. Experience-full upgrade
  choices pause combat until a card is confirmed; tracking loss and terminal
  settlement also stop the loop. The upgrade AttachmentPanel is world-locked
  after a one-shot HMD placement (no continuous head follow).
- Cache Scene Mesh data and avoid expensive whole-scene work every render frame.

## Key files

- `app/src/main/java/com/example/spatialsurvivor/Main.kt`: thin `DefaultStage`
  entry point and `PicoTheme` root.
- `app/src/main/java/com/example/spatialsurvivor/ui/SurvivorStage.kt`: Stage
  content, SpatialView setup, tracking lifecycles and Scene Mesh lifecycle.
- `app/src/main/java/com/example/spatialsurvivor/game/FixedStepClock.kt`: 90 Hz
  deterministic timestep with bounded catch-up work.
- `app/src/main/java/com/example/spatialsurvivor/game/GameLoopSystem.kt`: ECS
  system, gameplay-loop handoff and observable player HUD state.
- `app/src/main/java/com/example/spatialsurvivor/game/WaveRules.kt`: two-minute
  wave index, cumulative multipliers, entity-budget cap, unlocks and Boss time.
- `app/src/main/java/com/example/spatialsurvivor/game/GameSessionRuntime.kt`:
  wave, kills, Boss state, upgrade history and terminal settlement snapshots.
- `app/src/main/java/com/example/spatialsurvivor/game/SettlementGameplay.kt`:
  one-shot world-space settlement panel pose lock (1.0 m ahead, no head follow)
  and restart handoff.
- `app/src/main/java/com/example/spatialsurvivor/game/AppUiGameplay.kt`:
  one-shot world-space main-menu and permanent-progression panel pose lock
  (1.0 m ahead, Scene Mesh clearance, no head follow).
- `app/src/main/java/com/example/spatialsurvivor/game/AppUiRuntime.kt`:
  main-menu / permanent-panel visibility and open origin (main menu, settlement,
  pause).
- `app/src/main/java/com/example/spatialsurvivor/game/GameplaySceneFactory.kt`:
  pooled player, halo, four monster archetypes and projectile ECS visuals.
- `app/src/main/java/com/example/spatialsurvivor/game/SceneMeshRuntime.kt`:
  thread-safe anchor cache and off-render-thread navigation snapshot builder.
- `app/src/main/java/com/example/spatialsurvivor/game/SpatialTrackingRuntime.kt`:
  HMD, hand, eye and controller providers plus timestamped latest-data snapshots.
- `app/src/main/java/com/example/spatialsurvivor/game/TrackingContinuity.kt`:
  pure tracking freshness/recovery rules and session pause/resume state.
- `app/src/main/java/com/example/spatialsurvivor/game/SpatialHudGameplay.kt`:
  HMD world-pose view HUD AttachmentPanel, smoothing, recentering and overlay
  dimming.
- `app/src/main/java/com/example/spatialsurvivor/game/SpatialHudRules.kt`:
  pure countdown, progress, wrist-visibility and active-skill presentation rules.
- `app/src/main/java/com/example/spatialsurvivor/platform/LaunchActivity.kt`:
  runtime requests for spatial-data and eye-tracking permissions.
- `app/src/main/java/com/example/spatialsurvivor/player/PlayerGameplay.kt`:
  fixed-step HMD synchronization, targeting, attack, projectile and damage logic.
- `app/src/main/java/com/example/spatialsurvivor/player/PlayerStats.kt`:
  player defaults and pure deterministic combat rules.
- `app/src/main/java/com/example/spatialsurvivor/monster/MonsterComponent.kt`:
  archetype stats and spawn/movement/death runtime state.
- `app/src/main/java/com/example/spatialsurvivor/monster/MonsterAttackRules.kt`:
  shared aggro sprint and melee eligibility rules for every monster archetype.
- `app/src/main/java/com/example/spatialsurvivor/monster/MonsterGameplay.kt`:
  validated boundary/ceiling spawning, distance-budgeted A* movement and death.
- `app/src/main/java/com/example/spatialsurvivor/monster/MonsterUpdateBudgetRules.kt`:
  deterministic distance LOD cadence and staggered per-pool update phases.
- `app/src/main/java/com/example/spatialsurvivor/monster/CombatFeedbackRuntime.kt`:
  allocation-free hit pulse state and tightly capped gameplay hit-stop.
- `app/src/main/java/com/example/spatialsurvivor/monster/BossGameplay.kt`:
  10-minute mesh-boundary spawn, normal-monster clear, pursuit and area attack.
- `app/src/main/java/com/example/spatialsurvivor/monster/SpatialNavigationMap.kt`:
  bounded pure-Kotlin occupancy grid, pathfinding and spawn-point selection.
- `app/src/main/java/com/example/spatialsurvivor/monster/MonsterDropRuntime.kt`:
  death-to-EXP drop request bridge.
- `app/src/main/java/com/example/spatialsurvivor/exp/ExperienceGameplay.kt`:
  pooled crystal spawn, hover, attraction, absorption and level progression.
- `app/src/main/java/com/example/spatialsurvivor/upgrade/AutomaticUpgradeRuntime.kt`:
  deterministic skill-unlock and capped attribute-cycle policy plus HUD feedback.
- `app/src/main/java/com/example/spatialsurvivor/upgrade/UpgradeGameplay.kt`:
  one-shot world-space upgrade panel pose lock and selection handoff.
- `app/src/main/java/com/example/spatialsurvivor/upgrade/UpgradePanelPlacementRules.kt`:
  pure 1.0 m forward placement, yaw facing, and Scene Mesh clearance retreat.
- `app/src/main/java/com/example/spatialsurvivor/upgrade/UpgradeWeaponGameplay.kt`:
  concurrent fixed-step weapon skills and range visuals.
- `app/src/main/java/com/example/spatialsurvivor/upgrade/LevelUpEffectGameplay.kt`:
  pooled player-attached expansion ring.
- `app/src/main/java/com/example/spatialsurvivor/ui/settlement/SettlementScreen.kt`:
  ViewModel-backed victory/defeat presentation and restart interaction.
- `app/src/main/java/com/example/spatialsurvivor/ui/hud/components/`:
  SpatialUI unified wrist HUD, adaptive theme roles, progress bars,
  skill icons, lowered-arm fade and damage-flash animation.
- `app/src/main/AndroidManifest.xml`: Mixed MR Stage, upper-limb display and PICO
  permission declarations.

## Package evolution

Add gameplay features under these packages as the project grows:

- `player`: stats, automatic attacks, health and pickup logic
- `monster`: spawning, Scene Mesh navigation, collision and wave behavior
- `exp`: EXP entities, auto-pickup and progression
- `upgrade`: automatic unlock policy, weapon effects and level-up feedback
- `ui`: player-view HUD, health/EXP UI and results panel
- `game`: main loop, time, waves and boss coordination

## Build and run

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat connectedDebugAndroidTest
pico-cli app install app\build\outputs\apk\debug\app-debug.apk --device emulator-5554
pico-cli app launch com.example.spatialsurvivor --activity .platform.LaunchActivity --device emulator-5554
```

The PICO emulator cannot provide real hand-pose tracking and does not fully
represent eye tracking, Scene Mesh or device refresh-rate behavior. Validate
those capabilities and 90 fps performance on a compatible PICO headset.

The scene preallocates 18 normal monsters across four archetypes plus one final
Boss. Normal active pressure starts at 12, grows by 15% per wave, and is capped
at the existing 18-entity pool. Projectiles use an eight-entity pool, EXP crystals use a
20-entity pool, and orbiting swords use a six-entity pool. Keep these ownership
models so resources are not created and destroyed inside the fixed-step loop.
Scene Mesh updates are debounced and rebuilt on the `SceneMeshNavigation`
worker; each monster only requests a path about every 0.4 seconds.
Boundary monsters wait for a valid Scene Mesh navigation snapshot; do not add a
non-mesh fallback spawn because the gameplay contract requires mesh boundaries.
Do not add dynamic crystal rigid bodies: crystal contact is a gameplay sphere
used only for player absorption and intentionally ignores real-room geometry.

## Latest verification

- Android launcher icon refresh replaces the manifest-backed `@mipmap/ic_spatial_launcher` resource with the generated 1024 x 1024 SpatialSurvivor artwork, retains the source artwork under `branding/`, and passes `assembleDebug`.

- Modal-upgrade restoration reinstates experience-full combat suspension and a
  root-mounted 1.5 m eye-height AttachmentPanel. Only modal presentation,
  tracking, UI input and level-up effects continue while paused; monster,
  projectile, crystal, weapon and wave updates are skipped until a card is
  confirmed. The catalog implements white/blue/purple/gold rarity groups,
  late-level weight shifts, tenth-upgrade rare-or-better protection, duplicate
  and max-level filtering, unique-passive removal, optional fourth choice and
  forced legendary evolution replacement. The clean 11-gate workflow passes,
  including 66 JVM tests and SpatialUI admission with 0 errors/warnings. All 4
  instrumentation tests pass on headset `PB314XHGKC160016G`; APK SHA-256
  `2B85CC3157B41C44AA302B55C60C99C6D4AEB57D73B597967A104C4D9452D080` was
  installed at 2026-08-02 08:46:37 and is running as PID 485. The Android crash
  buffer is empty. The unattended headset currently reports its Stage tracking
  proxy unavailable, so natural kill/EXP/modal visual confirmation must be
  completed while the headset is worn; secure spatial compositor capture is not
  available through adb.
- HUD/automatic-attack regression repair uses local `-Z` (`Vector3.BACK`) as
  HMD visual forward and the corrected yaw sign, so the AttachmentPanel stays
  1.2 m in front rather than moving behind the headset. New players retain one
  starter energy-projectile stack. On headset `PB314XHGKC160016G`, both spatial
  permissions are granted, HUD mount logs report viewDirection `(0.0,-1.0)`,
  the 83-anchor Scene Mesh produced 942 walkable cells, and live logs show
  repeated 20-damage energy-projectile shots and monster deaths. JVM/build,
  SpatialUI design verification, and all 4 headset instrumentation tests pass;
  the crash buffer is empty.

- Persistent non-modal three-choice UI passes 60 JVM tests, 3 connected headset
  tests, the clean 11-gate anything-to-spatial-app workflow, `assembleDebug`,
  architecture checks, and SpatialUI design verification with 0 errors/warnings.
  Headset tests logged a pending level-2 choice, a selected Chain Lightning
  upgrade, and an explicit zero-health `DEFEAT` settlement. Historical live-run
  logs confirmed the reported apparent freeze was a real defeat: health fell
  `20 -> 12 -> 4 -> 0`, then `Settlement opened: outcome=DEFEAT`; a later pause
  was caused separately by lost HMD tracking while the headset screen was off.
  APK SHA-256 `3F87DCA1A31BC184E574D1C9C34C0C83D49CBB66A00CA5409773FEFF9C42F354`
  is installed on headset `PB314XHGKC160016G` and running as PID 14513 with an
  empty crash buffer. Secure/off-screen compositor capture rejected ADB
  screenshot output, so final visual placement still requires in-headset review.
- Ground-ring/aggression update passes `testDebugUnitTest` and `assembleDebug`,
  including coverage that every monster archetype can attack while chasing and
  that ceiling droppers cannot attack before landing. The final APK was installed
  and launched on headset `PB314XHGKC160016G` as PID 29567. HMD/hand/eye startup,
  stable tracking, Scene Mesh navigation and repeated boundary spawns were logged;
  the bounded crash watcher and crash buffer were clean. Direct headset inspection
  is still required for the horizontal torus visual because secure spatial layers
  are not available to ADB screenshots.
- Mixed MR redeployment on headset `PB314XHGKC160016G` verifies the installed
  container as `stageStyle=1` with immersion 0. The app started as PID 27496,
  all requested spatial/eye permissions were granted, HMD/hand/eye providers
  started successfully, tracking stabilized, the HUD attached, and a 56-anchor
  Scene Mesh produced more than 800 walkable cells plus valid boundary monster
  spawns. The bounded crash watcher and Android crash buffer were both clean.
  PICO's secure spatial compositor rejected ADB `screencap`, so final passthrough
  appearance still needs direct in-headset confirmation while the headset is worn.
- `testDebugUnitTest`, `assembleDebug`, and the single emulator
  `connectedDebugAndroidTest` suite pass. The JVM suite currently executes 56
  tests and the emulator suite executes 2 tests.
- SpatialUI design verification passes with 0 errors and 0 warnings; the app
  runtime graph contains no `androidx.compose.material:material` dependency.
- The anything-to-spatial-app validation workflow passes all machine gates
  cleanly: artifacts, Stage legality, implementation scan, Gradle discovery,
  smoke build, emulator install/launch, architecture, JVM tests and design
  admission.
- On managed emulator `emulator-5554`, runtime logs confirm player-core setup,
  HMD world-pose synchronization, EXP pool initialization, successful HMD/eye/
  controller provider startup, a 45-anchor Scene Mesh navigation rebuild, and
  wave-one boundary spawns using only the unlocked normal bug. The emulator does not
  expose controller tracking or a `CEILING` semantic, so controller confirmation
  and ceiling dropping require compatible hardware. The crash log buffer is
  empty after launch.
- Movement-speed upgrades stack in player state but deliberately do not scale
  tracked HMD translation; the physical-walking-only rule remains one-to-one.
- Blocking Scene Mesh triangles are rasterized as local footprints rather than
  one semantic-wide AABB; the current emulator room yields 260 walkable
  navigation cells. All verified monster spawns occur after that map is ready.
- ADB Full Space screenshots do not capture the volumetric 3D layer reliably;
  use a headset or the emulator viewport for final halo/monster visual QA.
- The HUD increment logs `Spatial HUD ready: wrist and lower-view health panels
  attached`. The emulator reports hand tracking as `PENDING` and cannot supply hand
  poses, so palm-axis threshold tuning and the raised/lowered wrist transition
  must be checked on a compatible PICO headset. The lower-view health pose runs
  from HMD tracking on the emulator. Capture evidence is stored under
  `artifacts/spatial-hud-permissions-granted.png`; the image is black because
  ADB does not capture the Full Space compositor.
- The emulator reports a 24 fps spatial compositor rate in this host session;
  it is functional launch evidence only, not proof of the 90 fps device target.
  Use a compatible PICO headset and the performance workflow for final profiling.
- The spatial-adaptation increment passes the clean 11-gate validation workflow.
  On `emulator-5554`, the app is installed and running as PID 31342; logs show
  HMD tracking entering recovery and resuming after the 0.15-second stability
  window, a 45-anchor/260-walkable-cell navigation rebuild, and a valid boundary
  monster spawn. The crash buffer is empty. Loss-path timing, physical furniture
  exclusion, and the 90 fps target still require headset/room validation.
- The unified-wrist/modal-upgrade/result increment passes 60 JVM tests,
  `assembleDebug`, SpatialUI design verification (0 errors), and the clean
  11-gate workflow with `ANDROID_SERIAL=emulator-5554`. The final debug APK is
  installed and running on emulator `emulator-5554` as PID 19736 with an empty
  crash buffer. The simulator granted eye-tracking permission; direct Stage
  `screencap` is black after permission because the spatial compositor is not
  exposed through adb, so real wrist/gesture visual QA still belongs on headset.
- Upgrade visibility hardening also passes the 60 JVM tests and `assembleDebug`.
  The final APK is installed and running on `emulator-5554` as PID 21214; the
  post-launch crash buffer is empty.
- Upgrade-panel scene-membership hardening passes the clean 11-gate incremental
  workflow, 64 JVM tests, and all 3 emulator instrumentation tests. Runtime logs
  confirm the AttachmentPanel is bound and mounted on the SpatialScene root with
  `distanceBias=0.25`; no missing-root warning or crash is present. The final APK
  SHA-256 is `AD8C6218E560BD245439629FF902A1D27451A214321C7DF48E7047806984C503`
  and is running on `emulator-5554` as PID 23687.
- View-HUD conversion passes the clean 11-gate incremental workflow and 66 JVM
  tests. The final APK SHA-256 is
  `5075191127B9F982D248EA878BE39367B66090B38BD42A1E51C35CDAAF724466`, installed
  and running on `emulator-5554` as PID 27381. Runtime logs confirm the HUD
  attached and synchronized to the HMD at 1.2 m / 15 degrees; the crash buffer
  is empty. Spatial capture is available at `artifacts/view-hud-launch.png`, but
  its compositor output is incomplete, so final visual comfort still needs an
  in-headset check.
- Automatic-upgrade restoration removes the final modal-upgrade presentation
  remnants: experience level-ups apply immediately without pausing combat, the
  first nine upgrades follow the deterministic skill order, and later upgrades
  cycle through capped attributes. The view HUD now publishes live damage,
  interval, range, projectile count, regeneration, pickup range, EXP multiplier,
  skill icons, and the 1.5-second upgrade message. Global damage and attack-speed
  scaling now applies to every automatic weapon rather than only the starter
  projectile.
- The increment passes `testDebugUnitTest`, `assembleDebug`, SpatialUI admission
  (0 errors, 0 warnings), and all 4 instrumentation tests on physical headset
  `PB314XHGKC160016G`. The final APK launched as PID 27279. Live logs confirm
  HUD/HMD synchronization, Scene Mesh navigation, repeated starter-projectile
  fire, crystal spawn/attraction/absorption, automatic level 2 with
  `ORBITING_SWORD stack=1`, and subsequent orbiting-sword damage while the starter
  weapon continued firing. The crash buffer is empty. PICO's secure spatial
  compositor rejected `screencap`, so direct visual comfort still requires the
  wearer to inspect the headset display.
