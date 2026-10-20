# Spec Delta

## Purpose

Defines how the system turns front-camera frames into discrete, target-agnostic gesture events, so that downstream action mapping never depends on how recognition is implemented.

## ADDED Requirements

### Requirement: Hand tracking from the front camera

The system SHALL capture frames from the front camera at a reduced analysis resolution and track a single primary hand, producing a normalized hand position and a set of hand landmarks for each processed frame.

#### Scenario: Hand enters the frame

- **WHEN** a hand becomes visible in the front camera view
- **THEN** the system produces a tracked hand position within one second of the hand entering the frame

#### Scenario: No hand in frame

- **WHEN** no hand is visible
- **THEN** the system reports an empty detection result and emits no gesture events

#### Scenario: Camera frame is mirrored

- **WHEN** the front camera produces a mirrored image
- **THEN** horizontal coordinates are normalized so that the user's leftward motion is reported as leftward motion in the reported gesture

### Requirement: Swipe detection from hand trajectory

The system SHALL detect a swipe from accumulated displacement of the hand position over consecutive frames, using the dominant axis to determine direction, and SHALL emit exactly one gesture event per detected swipe.

#### Scenario: Upward swipe

- **WHEN** the hand moves upward across consecutive frames by at least the configured displacement threshold, with consistent direction
- **THEN** the system emits a single `SWIPE_UP` event

#### Scenario: Downward swipe

- **WHEN** the hand moves downward across consecutive frames by at least the configured displacement threshold, with consistent direction
- **THEN** the system emits a single `SWIPE_DOWN` event

#### Scenario: Horizontal swipe takes priority on the dominant axis

- **WHEN** the hand's horizontal displacement exceeds the threshold and exceeds its vertical displacement
- **THEN** the system emits `SWIPE_LEFT` or `SWIPE_RIGHT` according to the horizontal direction

#### Scenario: Jitter is rejected

- **WHEN** the hand oscillates without accumulating the configured displacement in one direction
- **THEN** the system emits no swipe event

#### Scenario: Swipe is not duplicated

- **WHEN** a swipe event has just been emitted
- **THEN** further swipe events are suppressed for the configured cooldown period

#### Scenario: Recognition does not depend on gesture labels

- **WHEN** the gesture classifier returns an unstable or `None` label during a swipe
- **THEN** swipe detection still succeeds because it uses hand trajectory rather than the classifier label

### Requirement: Held static gesture detection

The system SHALL detect a held static gesture by requiring a stable classifier result above the configured confidence threshold while the hand remains nearly stationary for the configured dwell duration.

#### Scenario: Held fist triggers home

- **WHEN** the classifier reports a closed fist above the confidence threshold and the hand stays nearly stationary for the configured dwell duration
- **THEN** the system emits a single `FIST_HOLD` event

#### Scenario: Fist passing through during a swipe does not trigger

- **WHEN** the classifier briefly reports a closed fist while the hand is moving
- **THEN** no `FIST_HOLD` event is emitted

#### Scenario: Repeated hold requires release

- **WHEN** a `FIST_HOLD` event has been emitted and the fist remains held
- **THEN** no further `FIST_HOLD` events are emitted until the hand has been released and the gesture is performed again

### Requirement: Recognition outputs are independent of the target application

The recognition stage SHALL produce gesture events only, and SHALL NOT know which application is in the foreground or what action a gesture maps to.

#### Scenario: Same gesture, different targets

- **WHEN** the same upward swipe occurs while two different applications are in the foreground
- **THEN** the recognition stage emits the identical `SWIPE_UP` event in both cases

### Requirement: Detection stops when recognition is inactive

The system SHALL stop producing gesture events whenever recognition is inactive, including when the camera is not mounted.

#### Scenario: Inactive recognition emits nothing

- **WHEN** recognition is inactive
- **THEN** no gesture events are produced, even if the camera is briefly still delivering frames

### Requirement: Recognition is tunable

The system SHALL expose the displacement threshold, cooldown duration, confidence threshold, dwell duration, and direction inversion as user-configurable settings that take effect without restarting recognition.

#### Scenario: Threshold change applies immediately

- **WHEN** the user changes the displacement threshold while recognition is active
- **THEN** subsequent frames use the new threshold without restarting the camera or the recognition engine
