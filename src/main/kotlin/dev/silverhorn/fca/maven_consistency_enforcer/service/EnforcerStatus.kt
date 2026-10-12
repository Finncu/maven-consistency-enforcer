package dev.silverhorn.fca.maven_consistency_enforcer.service

import dev.silverhorn.fca.maven_consistency_enforcer.EnforcerBundle
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class EnforcerStatus(
    val checkedModules: AtomicInteger = AtomicInteger(0),
    val ignoredModules: AtomicInteger = AtomicInteger(0),
    val removedAttachedJars: AtomicInteger = AtomicInteger(0),
    val enforcementsCount: AtomicInteger = AtomicInteger(0),
    val processedLibraries: AtomicInteger = AtomicInteger(0),          // Maximum f?r den Progress Bar
    /**
     * Map: Modul-Name -> Liste der ersetzten Library-Dependencies (target module name).
     * Spiegelt wider, welche Maven-Library-Eintrge durch Modul-Eintrge ersetzt wurden.
     */
    val enforcedDependencies: MutableMap<String, MutableList<String>> = ConcurrentHashMap(),
    @Volatile var lastUpdated: String? = null,
    @Volatile var durationMs: Long = 0,

    // --- Post-Reload Pipeline Metriken ---
    /** True, sobald die Pipeline nach einem Enforcement mindestens einmal gelaufen ist. */
    @Volatile var pipelineRan: Boolean = false,
    val lastCompileErrors: AtomicInteger = AtomicInteger(0),
    val lastCompileWarnings: AtomicInteger = AtomicInteger(0),
    @Volatile var lastCompileAborted: Boolean = false,
    /** Namen der zuletzt gestarteten Run-Configurations. */
    val launchedRunConfigs: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf()),
    @Volatile var pipelineDurationMs: Long = 0
) {
    fun reset() {
        checkedModules.set(0)
        ignoredModules.set(0)
        removedAttachedJars.set(0)
        enforcementsCount.set(0)
        processedLibraries.set(0)
        enforcedDependencies.clear()
        resetPipeline()
    }

    fun resetPipeline() {
        pipelineRan = false
        lastCompileErrors.set(0)
        lastCompileWarnings.set(0)
        lastCompileAborted = false
        launchedRunConfigs.clear()
        pipelineDurationMs = 0
    }

    fun recordEnforcement(moduleName: String, targetModuleName: String) {
        enforcedDependencies
            .computeIfAbsent(moduleName) { java.util.Collections.synchronizedList(mutableListOf()) }
            .add(targetModuleName)
    }
}