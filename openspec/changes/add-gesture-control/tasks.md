# Tasks

## 1. Project scaffolding

- [x] 1.1 Create the Android Gradle project (Kotlin, Compose, application module, version catalog) with `minSdk 26`, `compileSdk`/`targetSdk 35`, and an `arm64-v8a` filter; verify `./gradlew :app:assembleDebug` succeeds
- [x] 1.2 Add pinned dependencies for CameraX, MediaPipe Tasks Vision, AndroidX Lifecycle service, DataStore, and Compose; verify the build resolves with no version conflicts
- [x] 1.3 Declare manifest permissions (`CAMERA`, `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA`) and the `<queries>` entry for launcher intents; verify `./gradlew :app:processDebugMainManifest` produces a manifest containing all declarations and no `QUERY_ALL_PACKAGES`
- [x] 1.4 Verify the project launches an empty Compose activity on the target device via `adb install` + `adb shell am start`

## 2. Domain core (pure Kotlin)

- [x] 2.1 Define `GestureEvent`, `GestureAction`, and `GestureConfig` types; verify they compile without Android imports (`./gradlew :app:testDebugUnitTest` compiles the domain sources)
- [x] 2.2 Implement `GestureEngine` swipe detection using accumulated palm displacement, dominant-axis direction, consistency check, threshold, and cooldown; verify unit tests cover each direction, jitter rejection, duplicate suppression, and horizontal-over-vertical priority
- [x] 2.3 Implement `FIST_HOLD` detection requiring confidence, stillness, dwell, and release-before-refire; verify unit tests cover a stationary hold, a fist formed while moving, and no re-fire while still held
- [x] 2.4 Implement `ActionResolver` with per-application overrides falling back to the global default and support for a no-action mapping; verify unit tests cover override precedence, fallback, no-action, and global-default changes propagating to apps without overrides
- [x] 2.5 Verify the engine emits identical events for identical landmark sequences regardless of the foreground package (no target awareness in the domain layer)

## 3. Frame conversion and inference

- [x] 3.1 Implement `FrameConverter` copying an `ImageProxy` honoring `rowStride`, applying `rotationDegrees`, and mirroring X for the front camera; verify unit tests with synthetic planes cover stride padding, all four rotations, and mirroring
- [x] 3.2 Implement the MediaPipe wrapper with LIVE_STREAM, capture-time timestamps and a monotonic guard, and a single in-flight inference flag; verify the timestamp guard unit test rejects non-increasing values and that a busy callback drops frames instead of queuing
- [ ] 3.3 Verify end-to-end on-device that landmarks track a hand and that frame processing holds 10-15 fps (`adb logcat` counters), with no `ImageProxy` leak warnings

## 4. Camera pipeline and foreground service

- [x] 4.1 Implement the camera foreground service (`LifecycleService`) that posts its notification before mounting the camera; verify on-device that `startForeground()` completes and no `ForegroundServiceDidNotStartInTimeException` occurs
- [x] 4.2 Bind CameraX `ImageAnalysis` (front camera, 640x480, `RGBA_8888`, `STRATEGY_KEEP_ONLY_LATEST`) to the service lifecycle and wire it to the converter and inference wrapper; verify frames flow and the camera privacy indicator appears only while mounted
- [x] 4.3 Implement mount/unmount control and the readiness (warming) state; verify gestures produce no events during warming and become active within about one second
- [x] 4.4 Implement camera disconnect detection with bounded backoff retry; verify by opening the system camera app while recognition is active and confirming recovery after the other app releases the camera
- [x] 4.5 Implement screen-off unmount and screen-on resume; verify by locking and unlocking the device while an enabled app is foreground

## 5. Accessibility service and activation

- [x] 5.1 Declare and implement the accessibility service with `canPerformGestures`, `canRetrieveWindowContent`, and `typeWindowStateChanged`; verify it appears in system accessibility settings and receives window state events
- [x] 5.2 Implement foreground-package tracking that ignores input method windows and system overlays; verify by opening the keyboard and a system dialog over an enabled app and confirming the tracked package does not change
- [x] 5.3 Wire the enabled-set check so that activation and dispatch are gated on the foreground package; verify that an unenabled app produces no camera mount and no actions
- [x] 5.4 Implement the settle delay for leaving an enabled app; verify that switching away and back within the delay does not remount the camera
- [x] 5.5 Implement node scrolling with `ACTION_SCROLL_FORWARD`/`ACTION_SCROLL_BACKWARD`, including the exhausted-direction case; verify in a long list that scrolling stops at the end without injecting a swipe
- [x] 5.6 Implement synthesized vertical swipe fallback and horizontal page swipes with edge-zone-safe coordinates; verify in an app without a scrollable node and in a horizontally paged app
- [x] 5.7 Implement `GLOBAL_ACTION_BACK` and `GLOBAL_ACTION_HOME`; verify both from an enabled app
- [ ] 5.8 Implement dispatch failure recording and cancellation handling when the user touches the screen; verify by touching during an injected swipe and confirming no retry

## 6. Settings, persistence, and overlay

- [ ] 6.1 Implement DataStore-backed settings for enabled applications, per-app mappings, global defaults, and tuning values; verify settings survive an app restart
- [ ] 6.2 Implement the settings surface for enabling applications (launcher query, no `QUERY_ALL_PACKAGES`) and confirm the enabled state persists across restart
- [ ] 6.3 Implement the per-app mapping editor showing inherited versus overridden entries, plus presets and reset-to-default; verify an override affects only that app and reset restores the global default
- [ ] 6.4 Implement tuning controls with clamped ranges and immediate effect; verify a threshold change applies to the next gesture without restarting the service
- [ ] 6.5 Implement the overlay status badge with non-touchable areas except the pause control, and brief action feedback; verify touches pass through to the app underneath
- [ ] 6.6 Implement the permission onboarding surface with live granted/missing state and links to platform settings; verify each missing item is reported and the ready state appears once all are granted
- [ ] 6.7 Implement the periodic health check for revoked accessibility or overlay permissions; verify by disabling the accessibility service and confirming the setup surface reports it

## 7. Integration verification on the Xiaomi 14

- [ ] 7.1 Verify the full golden path: grant permissions, enable a browser, swipe up/down to scroll, swipe left to go back, hold a fist to go home
- [ ] 7.2 Verify the home screen is inactive by default: no camera indicator and no gesture events while on the launcher
- [ ] 7.3 Verify an explicitly enabled home screen uses its preset (horizontal swipes page, vertical swipes and fist do nothing)
- [ ] 7.4 Verify the empty enabled set produces no camera mount and a clear first-run explanation
- [ ] 7.5 Verify HyperOS background survival: enable autostart and unrestricted battery, lock the app in recents, then confirm recognition still works after leaving the app idle for 30 minutes
- [ ] 7.6 Verify pause and resume from the overlay, and that pause survives switching applications
- [ ] 7.7 Measure battery and thermal behavior over a 30-minute session in an enabled app and confirm no sustained throttling; record the observed drain for reference
- [ ] 7.8 Verify low-light and fast-swipe behavior and record the tuning values that work best for the target device

## Workflow follow-up

- Archive the change after implementation and on-device verification are complete.
- Verify the archived specs under `openspec/specs/` contain the four capabilities with their scenarios intact.
