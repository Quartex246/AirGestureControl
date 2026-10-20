# Spec Delta

## Purpose

Defines how recognized gestures are mapped to actions per application and how those actions are executed on the device without root, including scrolling, back, home, and horizontal paging.

## ADDED Requirements

### Requirement: Two-stage gesture to action mapping

The system SHALL resolve every recognized gesture into an action through a mapping stage that first consults the foreground application's override and then falls back to the global default mapping.

#### Scenario: Application override wins

- **WHEN** the foreground application defines an override for a gesture
- **THEN** the system executes the overridden action rather than the global default

#### Scenario: Missing override falls back

- **WHEN** the foreground application has no override for a gesture
- **THEN** the system executes the global default action for that gesture

#### Scenario: Override disables an action

- **WHEN** the foreground application maps a gesture to no action
- **THEN** the system performs nothing for that gesture and reports no error

### Requirement: Default gesture mapping

The system SHALL provide a global default mapping of upward swipe to scroll forward, downward swipe to scroll backward, left swipe to back, right swipe to back, and held fist to home.

#### Scenario: First run defaults

- **WHEN** the application is used with no user configuration
- **THEN** upward and downward swipes scroll, horizontal swipes go back, and a held fist goes home

### Requirement: Configurable horizontal action per application

The system SHALL allow each application to map left and right swipes to back, horizontal page forward, horizontal page backward, or no action.

#### Scenario: Horizontal swipe configured as paging

- **WHEN** an application maps left swipe to horizontal page forward
- **THEN** a left swipe in that application advances to the next page instead of going back

#### Scenario: Horizontal swipe configured as back

- **WHEN** an application maps left swipe to back
- **THEN** a left swipe in that application triggers the system back action

### Requirement: Scrolling execution with fallback

The system SHALL attempt to scroll the foreground application's focused scrollable content, and SHALL fall back to an injected swipe gesture when no scrollable content can be scrolled.

#### Scenario: Scrollable node scrolls

- **WHEN** the foreground window exposes scrollable content that can scroll in the requested direction
- **THEN** the system scrolls that content without injecting touch events

#### Scenario: Scrollable content exhausted

- **WHEN** the scrollable content cannot scroll further in the requested direction
- **THEN** the system performs no injected swipe and performs no further action

#### Scenario: No scrollable node available

- **WHEN** the foreground window exposes no scrollable content that accepts the scroll
- **THEN** the system falls back to injecting a vertical swipe gesture in the requested direction

#### Scenario: Both strategies fail

- **WHEN** neither node scrolling nor gesture injection succeeds
- **THEN** the system performs no further action and records the failure for diagnostics

### Requirement: System actions without root

The system SHALL perform back and home actions through the platform accessibility mechanism without requiring root or a modification of system partitions.

#### Scenario: Back action

- **WHEN** a resolved action is back
- **THEN** the system triggers the platform back action

#### Scenario: Home action

- **WHEN** a resolved action is home
- **THEN** the system triggers the platform home action

### Requirement: Horizontal page action execution

The system SHALL execute horizontal page actions as an injected horizontal swipe that starts away from the screen's edge gesture zones and avoids the status and navigation bars.

#### Scenario: Page forward

- **WHEN** a resolved action is horizontal page forward
- **THEN** the system injects a horizontal swipe from the right portion of the screen toward the left portion

#### Scenario: Edge gesture zones avoided

- **WHEN** any injected horizontal swipe is executed
- **THEN** its start and end points remain outside the platform's edge gesture zones

### Requirement: Injected gestures respect user input

The system SHALL abandon an injected gesture when the user touches the screen during execution, and SHALL NOT retry automatically.

#### Scenario: User touches during injection

- **WHEN** the user touches the screen while an injected gesture is running
- **THEN** the injected gesture is cancelled and no retry occurs

### Requirement: Dispatch is limited to enabled applications

The system SHALL execute an action only when the foreground application is in the user's enabled set.

#### Scenario: Foreground application not enabled

- **WHEN** a gesture is recognized while the foreground application is not enabled
- **THEN** the system executes no action

### Requirement: Overlay does not block target application input

The system SHALL keep its overlay non-touchable except for explicit controls, so that the foreground application does not reject injected input as obscured.

#### Scenario: Status badge is non-interactive

- **WHEN** the overlay status badge is displayed over a target application
- **THEN** touches pass through to the target application
