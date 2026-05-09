# Jetbrains Marketplace
[![Get it from Marketplace](https://img.shields.io/badge/JetBrains-Marketplace-000000?style=for-the-badge&logo=jetbrains)](https://plugins.jetbrains.com/plugin/31634)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/31634?style=for-the-badge)](https://plugins.jetbrains.com/plugin/31634)
[![Rating](https://img.shields.io/jetbrains/plugin/r/rating/31634?style=for-the-badge)](https://plugins.jetbrains.com/plugin/31634)

# Maven Consistency Enforcer (MCE)

Stop fighting ghosts in your classpath!

MCE solves the frustrating problem of stale JAR artifacts preventing your code changes from reaching the running application in IntelliJ IDEA multimodule Maven projects.

**What it does:**
- Automatically detects stale JAR artifacts from ~/.m2/repository
- Automatically removes attached jars
- Replaces them with direct module-to-module dependencies after Maven reloads
- Manual purge action (Tools > MCE: Purge Stale Artifacts) to clean target/build directories
- Ensures what you see in your editor matches exactly what runs in your JVM

**Default behavior:**
- Triggers automatically after Maven > Reload Project
- Shows notifications about fixed dependencies
- Re-enforces consistency on every Maven reload
