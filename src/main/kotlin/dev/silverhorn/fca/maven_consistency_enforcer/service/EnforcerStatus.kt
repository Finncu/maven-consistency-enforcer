package dev.silverhorn.fca.maven_consistency_enforcer.service

import java.util.concurrent.atomic.AtomicInteger

data class EnforcerStatus(
    val checkedModules: AtomicInteger = AtomicInteger(0),
    val ignoredModules: AtomicInteger = AtomicInteger(0),
    val removedAttachedJars: AtomicInteger = AtomicInteger(0),
    val enforcementsCount: AtomicInteger = AtomicInteger(0),
    val processedLibraries: AtomicInteger = AtomicInteger(0),          // Maximum für den Progress Bar
    @Volatile var lastUpdated: String = "not yet executed",
    @Volatile var durationMs: Long = 0
) {
    fun reset() {
        checkedModules.set(0)
        ignoredModules.set(0)
        removedAttachedJars.set(0)
        enforcementsCount.set(0)
        processedLibraries.set(0)
    }
}