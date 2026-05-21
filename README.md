# Jetbrains Marketplace
[![Get it from Marketplace](https://img.shields.io/badge/JetBrains-Marketplace-000000?style=for-the-badge&logo=jetbrains)](https://plugins.jetbrains.com/plugin/31634)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/31634?style=for-the-badge)](https://plugins.jetbrains.com/plugin/31634)
[![Rating](https://img.shields.io/jetbrains/plugin/r/rating/31634?style=for-the-badge)](https://plugins.jetbrains.com/plugin/31634)

# Maven Consistency Enforcer (MCE)

Stop fighting ghosts in your classpath! MCE ensures your IntelliJ IDEA run-time classpath matches the code in your workspace in multi-module Maven projects by detecting and removing stale JAR artifacts and preferring local module sources when available.

What it does:
- Automatically removes attached JARs that shadow local modules
- Replaces library entries with module dependencies when a matching module exists
- Per-module enforcement configuration and global "All modules" toggle
- Shows notifications and a status bar widget with enforcement metrics

Default behavior:
- Triggers automatically after Maven > Reload Project and (optionally) on project startup
- Shows notifications about fixed dependencies
- Re-enforces consistency on every Maven reload

## Screenshots

Below are a few screenshots illustrating MCE in action.

### Status bar widget
Displays enforcement state and quick toggles for the plugin.
<p align="center">
  <img src="documentation/images/status.png" alt="Status bar widget" width="480" />
</p>

### Notification example
Informational notifications when the plugin fixes inconsistencies.
<p align="center">
  <img src="documentation/images/notification.png" alt="Notification example" width="480" />
</p>

### Before / After: Module enforcement
The plugin can replace library entries with local module dependencies. Examples (blurred to focus on the UI):
<p align="center">
  <img src="documentation/images/blurred-before-module-enforcement.png" alt="Before enforcement" width="420" />
</p>
<p align="center">
  <img src="documentation/images/blurred-after-module-enforcement.png" alt="After enforcement" width="420" />
</p>

### Settings
Quick access to per-module enforcement configuration and the global toggle.
<p align="center">
  <img src="documentation/images/blurred-settings.png" alt="Settings view" width="420" />
</p>

