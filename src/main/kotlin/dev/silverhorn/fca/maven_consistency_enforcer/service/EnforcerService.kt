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
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.WindowManager
import dev.silverhorn.fca.maven_consistency_enforcer.EnforcerBundle
import dev.silverhorn.fca.maven_consistency_enforcer.EnforcerConstants
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsState
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

    private val mvnPattern by lazy { Regex(EnforcerConstants.MAVEN_PATTERN) }
    private val gavPattern by lazy { Regex(EnforcerConstants.GAV_PATTERN) }

    val currentStatus = EnforcerStatus()

    private fun chooseEnforceModuleLinking(project: Project, state: EnforcerSettingsState): Boolean {
        val res = Messages.showYesNoDialog(
            project,
            EnforcerBundle.message("service.enforcer.enforceModuleLinking.dialog.message"),
            EnforcerBundle.message("service.enforcer.enforceModuleLinking.dialog.title"),
            Messages.getQuestionIcon()
        )
        return if (res != Messages.CANCEL)
            (Messages.YES == res).apply {
                state.enforceModuleLinking = this
            }
        else false
    }

    fun runConsistencyCheck() {
        ApplicationManager.getApplication().invokeAndWait {
            settings.state.enforceModuleLinking ?: chooseEnforceModuleLinking(project, settings.state)
        }

        val startTime = System.currentTimeMillis()
        currentStatus.reset()

        logger.info(EnforcerBundle.message("service.enforcer.consistencyCheck.start", moduleManager.modules.size))

        ApplicationManager.getApplication().invokeAndWait {
            if (settings.state.enforceModuleLinking!!)
                enforceModulesConsistency()
            cleanupAttachedJars()
        }
        ProjectRootManager.getInstance(project).incModificationCount()

        // Status-Meta-Informationen belegen
        currentStatus.lastUpdated =
            LocalTime.now().format(DateTimeFormatter.ofPattern(EnforcerConstants.DATE_FORMAT_PATTERN))
        currentStatus.durationMs = System.currentTimeMillis() - startTime

        logger.info(
            EnforcerBundle.message(
                "service.enforcer.consistencyCheck.completed",
                currentStatus.enforcementsCount
            )
        )

        // UI Thread-sicher benachrichtigen
        updateStatusBar()
    }

    fun cleanupAttachedJars() {
        val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)

        WriteAction.run<RuntimeException> {
            val tableModel = libraryTable.modifiableModel
            for (library in libraryTable.libraries) {
                if (library?.name?.contains(EnforcerConstants.ATTACHED_JAR_IDENTIFIER) ?: false) {
                    tableModel.removeLibrary(library)
                    currentStatus.removedAttachedJars.incrementAndGet()
                    logger.debug(EnforcerBundle.message("service.enforcer.cleanup.removedLibrary", library.name))
                }
            }
            tableModel.commit()
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
                    currentStatus.recordEnforcement(module.name, targetModule.name)
                }

                if (replacements.isNotEmpty()) {
                    model.commit()
                } else {
                    model.dispose()
                }

                updateStatusBar()
            }
        }
        ProjectRootManager.getInstance(project).incModificationCount()
    }

    private fun extractArtifactId(libraryName: String): String? {
        val gavRes: String? = mvnPattern.find(libraryName)?.groupValues[1]
            ?: gavPattern.find(libraryName)?.groupValues[1]
        if (gavRes == null)
            logger.warn(EnforcerBundle.message("service.enforcer.gavMismatch.warning", libraryName))
        return gavRes
    }

    private fun updateStatusBar() {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                (WindowManager.getInstance().getStatusBar(project)
                    ?.getWidget(MceStatusBarWidget.ID) as? MceStatusBarWidget)?.updateLabelText()
            }
        }
    }
}
