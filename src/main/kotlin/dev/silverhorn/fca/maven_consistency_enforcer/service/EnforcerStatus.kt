package dev.silverhorn.fca.maven_consistency_enforcer.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.WindowManager
import dev.silverhorn.fca.maven_consistency_enforcer.ui.MceStatusBarWidget
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

data class EnforcerStatus(
    val checkedModules: AtomicInteger = AtomicInteger(0),
    val ignoredModules: AtomicInteger = AtomicInteger(0),
    val cleanedDependencies: AtomicInteger = AtomicInteger(0), // Für den Ladebalken/Zähler gesamt
    val enforcementsCount: AtomicInteger = AtomicInteger(0),   // Ersetzungen libEntry -> module dep
    val enforcementLocations: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue(), // Thread-sichere Liste
    @Volatile var lastUpdated: String = "Noch nicht ausgeführt",
    @Volatile var durationMs: Long = 0,
    @Volatile var totalDependenciesToProcess: Int = 0          // Maximum für den Progress Bar
) {
    fun reset(totalDeps: Int = 0) {
        checkedModules.set(0)
        ignoredModules.set(0)
        cleanedDependencies.set(0)
        enforcementsCount.set(0)
        enforcementLocations.clear()
        totalDependenciesToProcess = totalDeps
    }
}