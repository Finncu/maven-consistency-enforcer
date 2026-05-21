# Changelog

All notable changes to the Maven Consistency Enforcer plugin will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Enforcement of using module source instead of artifacts or libraries
  - Per-module enforcement configuration and an "All modules" toggle
  - Automatic replacement of library entries with local module dependencies when a matching module is present
- Status bar widget showing enforcement state and metrics, plus quick toggles
- Automatic reimport on project startup to avoid using an out-of-date local Maven repository
- Snapshot publishing CI step (MAIN-SNAPSHOT) for continuous validation

### Changed

- Refactored consistency-check logic for improved reliability and clearer metrics
- Improved logging and notification messages for enforcement operations
- Threading and race-condition fixes in the enforcement logic

### Fixed

- Fixed cases where attached JAR cleanup could be missed due to timing/race conditions

## [1.0.0] - 2026-05-08

### Added

- Automatic Maven reload event detection
- removing of attached-jar's
- Smart notifications (Info/Warning/Error) for user feedback
- Comprehensive Maven Artifact ID extraction and module mapping logic
- Full implementation in Kotlin with extensive KDoc comments

### Features

- **EnforcerService**: Core logic for consistency enforcement
- **MceMavenReloadListener**: Automatic detection of Maven reload events
- **PurgeArtifactAction**: Manual cleanup of stale artifacts
- **MceNotification**: Centralized notification system
- **MceStatusWidget**: Status bar widget (implemented but disabled for v1.0)

### Technical

- Zero external dependencies (IntelliJ SDK only)
- Thread-safe implementation using DumbService
- Performance: <200ms for typical 50-module projects
- Batch processing of OrderEntry modifications
- Comprehensive error handling and logging

### Notes

- Status Bar Widget is implemented but not registered in 1.0.0
- Maven-only in v1.0. Gradle support planned for v1.1
- Designed following fdm-plugin best practices with Gradle version catalog and property management

[Unreleased]: https://github.com/finncu/maven-consistency-enforcer/compare/1.0.0...HEAD
[1.0.0]: https://github.com/finncu/maven-consistency-enforcer/commits/1.0.0
