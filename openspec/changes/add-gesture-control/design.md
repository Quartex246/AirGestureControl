# Design

## Context

See `proposal.md` for motivation and `specs/` for behavior contracts. The repository currently contains only the OpenSpec scaffold — this is a greenfield native Android project.

Constraints that shape the design:

- Target device is a Xiaomi 14 running HyperOS on Android 14/15. HyperOS is more aggressive than AOSP about killing background processes and disabling accessibility services.
- The camera is a **while-in-use** foreground service type on Android 14+. A camera-owning foreground service cannot be started from the background.
- `AccessibilityService.dispatchGesture` injects raw touch events, is cancelled by any user touch, and can be ignored by windows that reject obscured touches.
- MediaPipe's canned gesture labels are unreliable during fast motion, which is exactly when swipes happen.
- No root, so no direct event injection, no privileged APIs, and no system modifications.

## Goals / Non-Goals

**Goals:**

- Reliable discrete gesture recognition on a mid-range-to-flagship phone at 10-15 fps without excessive battery or thermal cost.
- Cross-application action execution with no root.
- Per-application behavior that is easy to understand and adjust.
- Recognition running only where the user asked for it, with an obvious visual state.

**Non-Goals:**

- Continuous/drag-style gesture tracking (the first version is discrete: one swipe, one action).
- Multi-hand or two-hand gestures.
- Gesture customization beyond the predefined set.
- Cloud processing, model training, or personalization.
- Distribution through Google Play (accessibility-based control of other apps is generally rejected).

## Decisions

### Decision: Accessibility service, not root or ADB-injected input

Use `AccessibilityService` with `canPerformGestures` and `canRetrieveWindowContent`. Root would give more reliable injection but is explicitly excluded. An ADB shell `input` shim requires a connected host and is unusable in daily life.

Consequence: actions are subject to accessibility semantics. Scrolling should prefer `ACTION_SCROLL_FORWARD` / `ACTION_SCROLL_BACKWARD` on the focused scrollable node, because that path is not affected by touch cancellation, fling physics, or obscured-touch rejection. Synthesized swipes are the fallback, not the primary mechanism.

### Decision: Recognition and mapping are separate layers

The recognition layer emits only `SWIPE_UP`, `SWIPE_DOWN`, `SWIPE_LEFT`, `SWIPE_RIGHT`, and `FIST_HOLD`. A resolver maps `(foreground package, gesture)` to an action using per-application overrides over a global default.

Alternative rejected: bake application rules into the recognition engine. That makes the same physical gesture behave differently depending on tuning, makes thresholds impossible to reason about, and duplicates state across the two layers.

### Decision: Swipes come from trajectory, not classifier labels

Compute the palm center as the mean of a stable subset of landmarks (wrist and the metacarpophalangeal joints), then accumulate displacement across frames. Use the dominant axis for direction, require consistent sign across frames, and gate on a displacement threshold.

Rationale: during a 300 ms swipe at 15 fps only ~4-5 frames are produced, and motion blur makes the classifier label jump between `Open_Palm`, `None`, and `Closed_Fist`. Accumulated displacement is far more stable than per-frame velocity, because inference latency varies frame to frame.

Alternative rejected: velocity thresholding. Unstable frame intervals make it erratic and hard to tune.

### Decision: Static gestures require dwell plus stillness

`FIST_HOLD` requires a high-confidence classifier result, near-zero palm displacement, and a dwell of roughly 800 ms. This prevents a fist formed mid-swipe from firing home.

Alternative rejected: use `Open_Palm` for home. It is the most common transient pose and would fire constantly. Also rejected: a custom pose, because it adds tuning cost for marginal benefit in a first version.

### Decision: Enabled set drives camera lifecycle, not just event filtering

The foreground package (from `TYPE_WINDOW_STATE_CHANGED`, filtered to ignore IME and system overlays) is compared against the enabled set. Entering an enabled app mounts the camera; leaving it unmounts after a short settle delay.

Why this is the right boundary:

- It satisfies "no camera on the home screen" without stopping the service.
- It avoids the Android 14+ restriction on starting a camera foreground service from the background, because the service is already running when the camera is mounted.
- It keeps memory cost to a few MB when idle instead of a continuously running camera, privacy indicator, and thermal load.

Alternatives considered:

| Approach | Why not |
| --- | --- |
| Start the whole foreground service when an enabled app appears | Relies on the `SYSTEM_ALERT_WINDOW` background-start exemption, which HyperOS does not reliably honor; adds 1-2 s cold start on every switch |
| Keep the camera always mounted | Privacy indicator always on, unnecessary battery and heat, contradicts the requirement |
| Use `UsageStatsManager` polling to detect the foreground app | Requires a special-access permission, adds polling latency, and duplicates information the accessibility service already receives for free |

### Decision: One process, shared state via observable state flow

The accessibility service and the camera foreground service run in the same process and share a singleton state holder. The accessibility service owns foreground-package tracking and action dispatch; the foreground service owns camera and inference.

Alternative rejected: put the accessibility service in a separate process. It complicates state sharing and gives no benefit here.

### Decision: CameraX `ImageAnalysis` with `STRATEGY_KEEP_ONLY_LATEST`

640x480 analysis stream at 10-15 fps, `RGBA_8888`, front camera. Frames are copied out of the `ImageProxy` honoring `rowStride`, rotated by `rotationDegrees`, mirrored on X for the front camera, then closed immediately. Only one inference may be in flight; a frame arriving while busy is dropped.

`STRATEGY_KEEP_ONLY_LATEST` prevents a backlog of stale frames from adding latency.

### Decision: MediaPipe LIVE_STREAM with capture timestamps

Use `imageInfo.timestamp` (converted to ms) with a monotonic guard, not the result-arrival time. LIVE_STREAM rejects non-increasing timestamps. Trajectory math must use capture-time deltas, otherwise varying inference latency corrupts velocity.

### Decision: Pure Kotlin domain core

The gesture engine, action resolver, and frame-coordinate transforms have no Android dependencies and are unit-testable. This is the part where threshold, hysteresis, cooldown, and mapping bugs are cheap to catch.

## Risks / Trade-offs

- **User touch cancels injected swipes.** Holding the phone means incidental touches will occasionally cancel a swipe → Mitigation: prefer node scrolling for the common case; accept the fallback failure and report it in diagnostics rather than retrying.
- **Node scrolling is not universal.** WebViews, Compose lists, and games may not expose a scrollable node, or may scroll different amounts than expected → Mitigation: keep the synthesized-swipe fallback; document that scroll distance is application-defined.
- **Front-camera ergonomics.** Holding the phone with one hand while gesturing with the other is tiring and the hand often leaves the frame → Mitigation: require only 25-50 cm distance, run at low resolution, show a status overlay, and keep sensitivity adjustable.
- **Low light and motion blur degrade landmarks.** → Mitigation: use accumulated displacement rather than single-frame velocity, expose the threshold, and reject inconsistent direction.
- **HyperOS kills the service or disables accessibility after reboot/update.** → Mitigation: onboarding for autostart, unrestricted battery, background pop-ups, and recents lock; periodic health check that surfaces the revoked state in the setup surface.
- **Camera preemption.** Opening the camera app or a video call disconnects the session → Mitigation: observe disconnect callbacks and retry with bounded backoff while an enabled app remains foreground.
- **Overlay can make target apps reject touches.** → Mitigation: overlay is `FLAG_NOT_TOUCHABLE` except the small pause button, kept minimal and out of the way.
- **Horizontal injection may collide with edge gestures.** → Mitigation: keep start/end points away from screen edges, use mid-screen vertical position, 200-250 ms duration.
- **Battery and heat.** → Mitigation: camera mounted only inside enabled apps, 10-15 fps at 640x480, pause control, screen-off handling.
- **Policy/distribution.** Accessibility-driven control of other apps is generally not acceptable for Play distribution → Mitigation: plan for sideloading; the feature is explicitly opt-in per application.
- **Privacy.** The camera sees the user whenever an enabled app is foreground → Mitigation: visible camera indicator (platform-enforced), overlay status, and a one-tap pause.

## Migration Plan

Greenfield application, no migration. Rollback is uninstalling the app; the accessibility service and overlay permissions are released with it.

## Open Questions

- Exact MediaPipe Tasks Vision and CameraX versions must be pinned at implementation time against the current stable releases; the design does not depend on a specific minor version.
- Whether the home screen's package name is stable across HyperOS updates. If it changes, the home screen entry may need to be matched by role rather than by a hardcoded package name.
- Whether node scrolling or synthesized swipes should be the default in the presence of a scrollable node that only partially scrolls. Defer until on-device testing shows which behavior feels better.
