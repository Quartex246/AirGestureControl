# Spec Delta

## Purpose

Defines when recognition runs at all: the system activates the camera and recognition pipeline only while an enabled application is in the foreground, and shuts them down everywhere else, including the home screen.

## ADDED Requirements

### Requirement: Enabled application set controls activation

The system SHALL activate recognition only while the foreground application is a member of the user's enabled application set, and SHALL keep recognition inactive for every other application and for the home screen.

#### Scenario: Enabled application enters foreground

- **WHEN** an enabled application becomes the foreground application
- **THEN** the system begins mounting the camera and preparing recognition

#### Scenario: Home screen is not enabled

- **WHEN** the home screen is in the foreground and the home screen has not been enabled
- **THEN** the camera is not mounted and no recognition occurs

#### Scenario: Home screen is explicitly enabled

- **WHEN** the user enables the home screen and the home screen is in the foreground
- **THEN** recognition runs on the home screen using the home screen's configured mapping

#### Scenario: Unenabled application in foreground

- **WHEN** an application outside the enabled set is in the foreground, including a game
- **THEN** the camera is not mounted and no gesture events are produced

### Requirement: Empty enabled set disables the feature

The system SHALL ship with an empty enabled set, so that recognition never runs until the user explicitly enables at least one application.

#### Scenario: First launch

- **WHEN** the application is first launched and no application has been enabled
- **THEN** no camera is mounted and the settings surface explains that at least one application must be enabled

### Requirement: Camera mounts only when needed

The system SHALL keep its background service alive while the feature is armed, but SHALL mount the camera only while an enabled application is in the foreground, and SHALL unmount the camera when leaving that application.

#### Scenario: Leaving an enabled application unmounts the camera

- **WHEN** the foreground application changes from an enabled application to the home screen or to a non-enabled application
- **THEN** the camera is unmounted and the camera privacy indicator turns off

#### Scenario: Returning to an enabled application remounts the camera

- **WHEN** the user returns to an enabled application
- **THEN** the camera is remounted and recognition resumes

#### Scenario: Brief switching does not thrash

- **WHEN** the foreground application changes away and back within the configured settle delay
- **THEN** the camera is not unmounted and remounted

### Requirement: Recognition readiness is reported

The system SHALL report a warming state between mounting the camera and producing usable recognition results, so that gesture input during that window is not treated as a failure.

#### Scenario: Warming state suppresses gestures

- **WHEN** the camera has been mounted but recognition has not yet produced a usable result
- **THEN** no gesture events are emitted and the status surface indicates that recognition is preparing

### Requirement: Screen off stops recognition

The system SHALL unmount the camera when the screen turns off and SHALL resume only after the screen turns on and an enabled application is in the foreground.

#### Scenario: Screen off

- **WHEN** the screen turns off while recognition is active
- **THEN** the camera is unmounted and no frames are processed

#### Scenario: Screen on again

- **WHEN** the screen turns on with an enabled application in the foreground
- **THEN** the camera is remounted and recognition resumes

### Requirement: Camera preemption is recovered

The system SHALL detect that the camera session was lost to another application and SHALL retry mounting with a bounded backoff while an enabled application remains in the foreground.

#### Scenario: Another application takes the camera

- **WHEN** another application acquires the camera while recognition is active
- **THEN** the system reports that recognition is unavailable and retries later

#### Scenario: Camera becomes available again

- **WHEN** the camera is released by the other application
- **THEN** the system remounts the camera and resumes recognition without user action

### Requirement: User pause is respected

The system SHALL provide a pause control that suspends recognition without disabling the feature, and SHALL keep recognition suspended until the user resumes.

#### Scenario: User pauses

- **WHEN** the user activates pause while recognition is active
- **THEN** the camera is unmounted and no gesture events are produced

#### Scenario: Pause survives application switching

- **WHEN** the user has paused recognition and switches between applications
- **THEN** recognition remains suspended until the user resumes it

### Requirement: Foreground detection ignores transient windows

The system SHALL determine the foreground application while ignoring input method windows and system overlays.

#### Scenario: Keyboard appears

- **WHEN** the input method window becomes visible over an enabled application
- **THEN** the system still treats the underlying enabled application as the foreground application

#### Scenario: System dialog appears

- **WHEN** a system overlay such as a permission dialog appears over an enabled application
- **THEN** the system does not treat the overlay as the foreground application

### Requirement: Activation state is observable

The system SHALL expose its activation state so that the status surface can distinguish active recognition, inactive because the application is not enabled, preparing, paused, and unavailable.

#### Scenario: State reflects current condition

- **WHEN** any activation condition changes
- **THEN** the exposed activation state updates accordingly without requiring a restart
