package dev.silverhorn.fca.maven_consistency_enforcer.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.wm.WindowManager
import dev.silverhorn.fca.maven_consistency_enforcer.notifications.MceNotification
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsStateService
import dev.silverhorn.fca.maven_consistency_enforcer.ui.MceStatusBarWidget
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.io.File
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

        ApplicationManager.getApplication().invokeAndWait {
            if (settings.state.forceLocalModules)
                enforceModulesConsistency()
            cleanupAttachedJars()
        }

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
                    logger.debug("MCE: Removed unused project library " + library.name)
                }
            }
            tableModel.commit()
        }
    }
fun fixMavenRepository() {
    logger.info("MCE: Starte globale Classpath-Korrektur über LibraryTable (IDEA-377511)")
    
    // Das funktioniert garantiert zum Loggen
    MceNotification.showInfo(project, "MCE: checking repo usage") //

    val expectedRepoPath = mavenProjectsManager.generalSettings.localRepository ?: return
    val expectedCanonical = File(expectedRepoPath).canonicalPath
    val defaultM2Path = File(System.getProperty("user.home"), ".m2/repository").canonicalPath

    if (expectedCanonical == defaultM2Path) {
        logger.info("MCE: Keine Korrektur nötig, da Default-Repo genutzt wird.")
        return
    }

    val librariesToFix = mutableListOf<Pair<String, List<String>>>()

    // 1. READ ACTION: Betroffene Libraries identifizieren
    ReadAction.run<RuntimeException> {
        val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
        for (library in libraryTable.libraries) {
            if (library == null || library.name == null) continue

            val classesUrls = library.getUrls(OrderRootType.CLASSES)
            val buggyUrls = classesUrls.filter { url ->
                url.contains(".m2/repository") && !url.contains(expectedCanonical)
            }

            if (buggyUrls.isNotEmpty()) {
                librariesToFix.add(library.name!! to buggyUrls)
            }
        }
    }

    if (librariesToFix.isEmpty()) {
        logger.info("MCE: Keine fehlerhaften Pfade gefunden.")
        return
    }

    // 2. WRITE ACTION auf dem EDT
    ApplicationManager.getApplication().invokeLater {
        WriteAction.run<RuntimeException> {
            val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
            val tableModel = libraryTable.modifiableModel
            var anyLibraryChanged = false
            var fixCount = 0

            for ((libName, buggyUrls) in librariesToFix) {
                val libraryInTable = tableModel.getLibraryByName(libName) ?: continue
                val libModel = libraryInTable.modifiableModel

                for (url in buggyUrls) {
                    val correctedUrl = url.replace(defaultM2Path, expectedCanonical)
                    
                    // Root entfernen und korrigierten Pfad hinzufügen
                    libModel.removeRoot(url, OrderRootType.CLASSES)
                    libModel.addRoot(correctedUrl, OrderRootType.CLASSES)
                    
                    currentStatus.enforcementsCount.incrementAndGet()
                    fixCount++
                    logger.debug("MCE: Root korrigiert: $libName -> $correctedUrl")
                }

                libModel.commit()
                anyLibraryChanged = true
            }

            if (anyLibraryChanged) {
                // Das Tabellenmodell committen
                tableModel.commit()

                // CHIRURGISCHER SCHRITT: Den globalen ModificationCount des Projekts hochzählen!
                // Das zwingt IntelliJ, alle externen Caches (inkl. der Project Structure UI) wegzuschmeißen.
                ProjectRootManager.getInstance(project).incModificationCount()

                logger.info("MCE: Globale LibraryTable-Änderungen committet und Caches invalidiert.")
                
                updateStatusBar()
                MceNotification.showInfo(
                    project, 
                    "MCE: Fixed $fixCount library paths from resetting bug." //
                )
            } else {
                tableModel.dispose()
            }
        }
    }
} 
private fun enforceModulesConsistency() {
        currentStatus.ignoredModules.set(settings.state.excludedModules.size)
        val moduleMap = moduleManager.modules.mapNotNull { module ->
            if (settings.state.excludedModules.contains(module.name)) {
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
