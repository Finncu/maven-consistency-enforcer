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
    @Volatile var durationMs: Long = 0
) {
    fun reset() {
        checkedModules.set(0)
        ignoredModules.set(0)
        removedAttachedJars.set(0)
        enforcementsCount.set(0)
        processedLibraries.set(0)
        enforcedDependencies.clear()
    }

    fun recordEnforcement(moduleName: String, targetModuleName: String) {
        enforcedDependencies
            .computeIfAbsent(moduleName) { java.util.Collections.synchronizedList(mutableListOf()) }
            .add(targetModuleName)
    }
}