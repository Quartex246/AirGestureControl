# Spec Delta

## Purpose

Defines the user-facing surfaces needed to make the feature usable: permission onboarding, application enablement, per-application gesture mapping, sensitivity tuning, and a status indicator that explains what the system is currently doing.

## ADDED Requirements

### Requirement: Permission onboarding

The system SHALL present a setup surface that identifies each required permission, explains why it is needed, reports whether it is currently granted, and links to the platform setting where it can be granted.

#### Scenario: Missing permission is reported

- **WHEN** any required permission or service is not granted
- **THEN** the setup surface reports it as missing and provides a control to open the relevant platform setting

#### Scenario: Camera permission denied

- **WHEN** camera permission is denied
- **THEN** the setup surface explains that gesture recognition cannot run and does not start the camera

#### Scenario: Accessibility service disabled

- **WHEN** the accessibility service is not enabled
- **THEN** the setup surface reports that actions cannot be executed and provides a control to open the accessibility settings

#### Scenario: Setup completes

- **WHEN** every required permission and service is granted
- **THEN** the setup surface indicates that the feature is ready and at least one application can be enabled

### Requirement: Application enablement

The system SHALL let the user choose which installed applications the feature applies to, and SHALL show the current enablement state for each application.

#### Scenario: Enable an application

- **WHEN** the user enables an application
- **THEN** recognition becomes active whenever that application is in the foreground

#### Scenario: Disable an application

- **WHEN** the user disables an application
- **THEN** recognition stops for that application and the camera is unmounted if it is currently in the foreground

#### Scenario: Enabled applications persist

- **WHEN** the user restarts the device or the application
- **THEN** the previously enabled applications remain enabled

### Requirement: Per-application gesture mapping

The system SHALL let the user view and change the gesture-to-action mapping for each enabled application, showing which entries are inherited from the global default and which are overridden.

#### Scenario: View mapping

- **WHEN** the user opens an enabled application's mapping
- **THEN** the current effective action for each gesture is shown along with whether it is inherited or overridden

#### Scenario: Override a gesture

- **WHEN** the user assigns a different action to a gesture for one application
- **THEN** only that application's mapping changes and the global default is unaffected

#### Scenario: Reset to default

- **WHEN** the user resets an application's mapping to the global default
- **THEN** that application's overrides are removed and its effective mapping matches the global default

#### Scenario: Preset applies a coherent mapping

- **WHEN** the user selects a preset for an application
- **THEN** all gestures for that application are set to the preset's mapping in one step

### Requirement: Global default mapping editing

The system SHALL let the user edit the global default gesture mapping, and applications without overrides SHALL follow the updated default.

#### Scenario: Change global default

- **WHEN** the user changes the global default action for a gesture
- **THEN** every application without an override for that gesture uses the new action

### Requirement: Sensitivity tuning

The system SHALL expose controls for swipe displacement threshold, gesture cooldown, static gesture confidence, static gesture dwell duration, and direction inversion.

#### Scenario: Adjust sensitivity while active

- **WHEN** the user adjusts any tuning control while recognition is active
- **THEN** the change applies to subsequent gestures without restarting the service

#### Scenario: Invalid value is prevented

- **WHEN** the user attempts to set a tuning value outside its supported range
- **THEN** the control clamps the value to the supported range

### Requirement: Status indicator

The system SHALL display a compact overlay while the service is armed that distinguishes preparing, active recognition, inactive because the current application is not enabled, paused, and unavailable.

#### Scenario: Active over an enabled application

- **WHEN** recognition is active over an enabled application
- **THEN** the overlay indicates that recognition is active

#### Scenario: Inactive over a non-enabled application

- **WHEN** a non-enabled application is in the foreground
- **THEN** the overlay indicates that the feature is not active for that application, or is hidden if the user chose to hide it

#### Scenario: Action feedback

- **WHEN** an action is executed
- **THEN** the overlay briefly shows which action was performed

### Requirement: Pause control

The system SHALL provide a control to suspend and resume recognition without leaving the foreground application.

#### Scenario: Pause from overlay

- **WHEN** the user activates the pause control
- **THEN** recognition is suspended and the overlay indicates the paused state

#### Scenario: Resume from overlay

- **WHEN** the user activates the control again while paused
- **THEN** recognition resumes if an enabled application is in the foreground

### Requirement: Health check for revoked permissions

The system SHALL detect when a previously granted permission or the accessibility service has been revoked and SHALL surface that condition in the setup surface.

#### Scenario: Accessibility service revoked after device restart

- **WHEN** the accessibility service is no longer enabled but the feature was previously configured
- **THEN** the setup surface reports that actions cannot be executed and prompts the user to re-enable it

#### Scenario: Overlay permission revoked

- **WHEN** the overlay permission is revoked
- **THEN** recognition continues if otherwise permitted and the setup surface reports that status feedback is unavailable

### Requirement: First-run guidance

The system SHALL guide the user through the minimum configuration needed before gestures can work.

#### Scenario: Feature not yet usable

- **WHEN** the user has not enabled any application or has not granted the required permissions
- **THEN** the system explains what remains to be done instead of appearing broken
