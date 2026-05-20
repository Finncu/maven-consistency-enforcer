package dev.silverhorn.fca.maven_consistency_enforcer.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.containers.stream
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsState
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsStateService
import dev.silverhorn.fca.maven_consistency_enforcer.ui.MceStatusBarWidget
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicInteger
@Service(Service.Level.PROJECT)
class EnforcerService(private val project: Project) {

    private val logger = Logger.getInstance(EnforcerService::class.java)
    private val consistencyChanges = mutableListOf<String>()
    private val moduleManager: ModuleManager by lazy { project.service() }
    private val mavenProjectsManager: MavenProjectsManager by lazy { project.service() }
    private val settings: EnforcerSettingsStateService by lazy { project.service() }

    private val mvnPattern by lazy { Regex("^Maven: ?[^ :]+:([^ :]+)") }
    private val gavPattern by lazy { Regex("[^ :]+:([^ :]+):[^ :]+.*") }

    // DAS GLOBALE STATUS-OBJEKT
    val currentStatus = EnforcerStatus()

    fun runFullConsistencyCheck(): Int {
        val startTime = System.currentTimeMillis()
        consistencyChanges.clear()

        // Ungefähre Schätzung der zu verarbeitenden Elemente für den Ladebalken berechnen
        val approxTotalDeps = moduleManager.modules.size * 2 // Grober Richtwert oder dynamisch ermittelbar
        currentStatus.reset(approxTotalDeps)

        logger.info("MCE: Starting full consistency check for ${moduleManager.modules.size} modules")

        var totalChanges = 0
        totalChanges += enforceModulesConsistency()
        totalChanges += cleanupAttachedJars()

        // Status-Meta-Informationen belegen
        currentStatus.lastUpdated = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        currentStatus.durationMs = System.currentTimeMillis() - startTime

        logger.info("MCE: Consistency check completed. Total replacements: $totalChanges")

        // UI Thread-sicher benachrichtigen
        updateStatusBar()

        return totalChanges
    }

    fun cleanupAttachedJars(): Int {
        val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
        var removedCount = 0

        WriteAction.run<RuntimeException> {
            val tableModel = libraryTable.modifiableModel
            for (library in libraryTable.libraries) {
                if (library?.name?.contains("ATTACHED-JAR") ?: false) {
                    tableModel.removeLibrary(library)
                    removedCount++
                    // Inkrementiere bereinigte Dependencies global
                    currentStatus.cleanedDependencies.incrementAndGet()
                    consistencyChanges.add("Removed project library: " + library?.name)
                    logger.debug("MCE: Removed unused project library " + library?.name)
                }
            }
            tableModel.commit()
        }
        return removedCount
    }

    private fun enforceModulesConsistency(): Int {
        val changesCount = AtomicInteger(0)
        val moduleMap = moduleManager.modules.mapNotNull { module ->
            if (settings.state.excludedModules.contains(module.name)) {
                currentStatus.ignoredModules.incrementAndGet() // Zähle ignoriertes Modul
                return@mapNotNull null
            }
            val mavenProject = mavenProjectsManager.findProject(module) ?: return@mapNotNull null
            val artifactId = mavenProject.mavenId.artifactId ?: return@mapNotNull null
            artifactId to module
        }.toMap()

        WriteAction.run<RuntimeException> {
            for (module in moduleManager.modules) {
                if (settings.state.excludedModules.contains(module.name)) continue

                currentStatus.checkedModules.incrementAndGet() // Zähle überprüftes Modul
                val model = ModuleRootManager.getInstance(module).modifiableModel
                val replacements = model.orderEntries.filterIsInstance<LibraryOrderEntry>().mapNotNull { entry ->
                    entry.libraryName?.let { moduleMap[this.extractArtifactId(it)] }?.let {
                        entry to it
                    }
                }

                for ((libEntry, targetModule) in replacements) {
                    model.removeOrderEntry(libEntry)
                    model.addModuleOrderEntry(targetModule)
                    changesCount.incrementAndGet()

                    // Status belegen
                    currentStatus.enforcementsCount.incrementAndGet()
                    currentStatus.cleanedDependencies.incrementAndGet()

                    val logEntry = "${module.name}: '${libEntry.libraryName}' -> Module '${targetModule.name}'"
                    if (!currentStatus.enforcementLocations.contains(logEntry)) {
                        currentStatus.enforcementLocations.add(logEntry)
                    }
                }

                if (replacements.isNotEmpty()) {
                    model.commit()
                } else {
                    model.dispose()
                }

                // Zwischenstand an Statusleiste senden bei längeren Durchläufen
                updateStatusBar()
            }
        }
        return changesCount.get()
    }

    private fun extractArtifactId(libraryName: String): String? {
        val gavRes: String? = mvnPattern.find(libraryName)?.groupValues[1]
            ?: gavPattern.find(libraryName)?.groupValues[1]
        if (gavRes == null)
            logger.warn("$libraryName doesnt match GAV-C")
        return gavRes
    }

    private fun updateStatusBar() {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                WindowManager.getInstance().getStatusBar(project)?.updateWidget(MceStatusBarWidget.ID)
            }
        }
    }
}