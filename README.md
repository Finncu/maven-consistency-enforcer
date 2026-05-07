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

