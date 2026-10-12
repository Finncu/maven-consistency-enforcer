# Changelog

All notable changes to the Maven Consistency Enforcer plugin will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

> Theme: decouple MCE from the pure Maven reload lifecycle and evolve it into a
> configurable **post-reload automation pipeline**. After enforcement runs, MCE can
> trigger follow-up steps (compile, run configurations, custom tasks) in a defined,
> observable order.

### Added

- **Post-reload automation pipeline**
  - Ordered, configurable step chain: `enforce ? compile ? run configuration ? custom tasks`
  - Per-step enable/disable toggle with persistent workspace settings
  - Conditional execution: only run follow-up steps when enforcement actually changed something (skip no-op reloads)
  - Fail-fast handling: stop the pipeline on compile errors and surface them in a notification
  - Debounce / throttle to avoid repeated pipeline runs on rapid successive Maven reloads
  - Manual "Run pipeline now" action plus a quick-action entry in the status bar widget

- **Compile step enhancements**
  - Module-selective compilation: compile only enforced/changed modules instead of the whole project
  - Choice between "Build" and "Rebuild" semantics
  - Optional parallel/incremental compilation hint
  - scope (test or without test)
  - Surface compilation results (errors/warnings count) in the status bar widget

- **Run configuration automation**
  - Select one or more existing run configurations to launch after a successful compile
  - Support "Before launch" task chaining and run vs. debug mode selection
  - Optional auto-restart of a long-running configuration (e.g. application server) after reload

- **Custom post-processing tasks**
  - Run arbitrary external tools / scripts after reload (e.g. code generation, annotation processors, npm/gradle sub-tasks)
  - Per-module post-processing configuration
  - Optional cache/stale-output invalidation step before compiling

- **Observability & UX**
  - Per-step timing and outcome reported in the status bar widget popup and in a pipeline summary notification
  - Dry-run / preview mode that lists the planned actions before executing them
  - Extend `mce-settings.xml` template support to cover all new pipeline options for templated workspaces

- persistent workspace settings for post run configs

## [1.3.0] - 2026-06-04

### Added

- Localization support
- Maven sync health check
- Persistence cache for improved performance
- Enhanced Maven repository path validation and correction
- Improved enforcement status tracking
- Configurable Maven reload types

### Changed

- `forceLocalModules` and repository health check will be questioned on first run
- Enhanced enforcement features

### Fixed

- Status tracking widget now reliably updates after a run

### Technical

- Updated plugin build to extract the plugin description from `README.md` (Gradle) to centralize documentation and avoid duplication (commit: 9033a19).
- CI / pipeline improvements: test-pipeline adjustments and revisions to the publish command; some package deletion steps were commented out to avoid accidental removals (commits on 2026-06-03).
- Repository and documentation housekeeping: README and CHANGELOG updates and small merge/cleanup commits.

## [1.2.0] - 2026-05-21

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

- Designed following fdm-plugin best practices with Gradle version catalog and property management

[Unreleased]: https://github.com/finncu/maven-consistency-enforcer/compare/1.3.0...HEAD
[1.3.0]: https://github.com/finncu/maven-consistency-enforcer/compare/1.2.0...1.3.0
[1.2.0]: https://github.com/finncu/maven-consistency-enforcer/compare/1.0.0...1.2.0
[1.0.0]: https://github.com/finncu/maven-consistency-enforcer/commits/1.0.0
