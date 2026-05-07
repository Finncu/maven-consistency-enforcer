# Changelog

All notable changes to the Maven Consistency Enforcer plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Automatic Maven reload event detection
- Stale JAR artifact detection and replacement with module dependencies
- Manual purge action for target/build directories via Tools > MCE: Purge Stale Artifacts
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

---

### Notes
- Status Bar Widget is implemented but not registered in v1.0. See UPGRADE.md for enabling in v1.1
- Maven-only in v1.0. Gradle support planned for v1.1
- Designed following fdm-plugin best practices with Gradle version catalog and property management


