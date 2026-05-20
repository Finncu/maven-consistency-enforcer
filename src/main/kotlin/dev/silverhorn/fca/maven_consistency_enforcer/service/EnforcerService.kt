package dev.silverhorn.fca.maven_consistency_enforcer.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.wm.WindowManager
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsStateService
import dev.silverhorn.fca.maven_consistency_enforcer.ui.MceStatusBarWidget
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Service(Service.Level.PROJECT)
class EnforcerService(private val project: Project) {

    private val logger = Logger.getInstance(EnforcerService::class.java)
    private val moduleManager: ModuleManager by lazy { project.service() }
    private val mavenProjectsManager: MavenProjectsManager by lazy { project.service() }
    private val settings: EnforcerSettingsStateService by lazy { project.service() }

    private val mvnPattern by lazy { Regex("^Maven: ?[^ :]+:([^ :]+)") }
    private val gavPattern by lazy { Regex("[^ :]+:([^ :]+):[^ :]+.*") }

    val currentStatus = EnforcerStatus()

    fun runFullConsistencyCheck() {
        val startTime = System.currentTimeMillis()

        currentStatus.reset()

        logger.info("MCE: Starting full consistency check for ${moduleManager.modules.size} modules")
        enforceModulesConsistency()
        cleanupAttachedJars()
        // Status-Meta-Informationen belegen
        currentStatus.lastUpdated = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        currentStatus.durationMs = System.currentTimeMillis() - startTime

        logger.info("MCE: Consistency check completed. Total replacements: ${currentStatus.enforcementsCount}")

        // UI Thread-sicher benachrichtigen
        updateStatusBar()
    }

    fun cleanupAttachedJars() {
        val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)

        WriteAction.run<RuntimeException> {
            val tableModel = libraryTable.modifiableModel
            for (library in libraryTable.libraries) {
                if (library?.name?.contains("ATTACHED-JAR") ?: false) {
                    tableModel.removeLibrary(library)
                    currentStatus.removedAttachedJars.incrementAndGet()
                    logger.debug("MCE: Removed unused project library " + library?.name)
                }
            }
            tableModel.commit()
        }
    }

    private fun enforceModulesConsistency() {
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

                currentStatus.checkedModules.incrementAndGet()
                val model = ModuleRootManager.getInstance(module).modifiableModel
                val replacements = model.orderEntries.filterIsInstance<LibraryOrderEntry>().mapNotNull { entry ->
                    currentStatus.processedLibraries.incrementAndGet()
                    entry.libraryName?.let { moduleMap[this.extractArtifactId(it)] }?.let {
                        entry to it
                    }
                }

                for ((libEntry, targetModule) in replacements) {
                    model.removeOrderEntry(libEntry)
                    model.addModuleOrderEntry(targetModule)
                    currentStatus.enforcementsCount.incrementAndGet()
                }

                if (replacements.isNotEmpty()) {
                    model.commit()
                } else {
                    model.dispose()
                }

                updateStatusBar()
            }
        }
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