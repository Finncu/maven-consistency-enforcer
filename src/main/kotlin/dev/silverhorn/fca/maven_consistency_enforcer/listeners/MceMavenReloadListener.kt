package dev.silverhorn.fca.maven_consistency_enforcer.listeners

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import dev.silverhorn.fca.maven_consistency_enforcer.EnforcerBundle
import dev.silverhorn.fca.maven_consistency_enforcer.service.EnforcerService
import dev.silverhorn.fca.maven_consistency_enforcer.notifications.MceNotification
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsStateService
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.idea.maven.project.MavenProject

class MceMavenReloadListener(private val project: Project) : MavenImportListener {

    private val logger = Logger.getInstance(MceMavenReloadListener::class.java)
    private val enforcerService: EnforcerService by lazy { project.service() }
    private val progressManager: ProgressManager by lazy { ProgressManager.getInstance() }
    private val settings: EnforcerSettingsStateService by lazy { project.service<EnforcerSettingsStateService>() }

    override fun importFinished(importedProjects: Collection<MavenProject>, newModules: List<com.intellij.openapi.module.Module>) {
        if (!settings.state.isEnabled) return

        if (importedProjects.isEmpty()) {
            return
        }

        MceNotification.showInfo(project, EnforcerBundle.message("listener.mavenReload.info.checkingConsistency"))

        progressManager.run(object : Task.Backgroundable(project, EnforcerBundle.message("listener.mavenReload.task.title"), false) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                try {
                    indicator.text = EnforcerBundle.message("listener.mavenReload.task.indicatorText")
                    indicator.isIndeterminate = true

                    // Übergabe der Einstellungen an den Service
                    enforcerService.runConsistencyEnforcement()

                    var changeCount = enforcerService.currentStatus.enforcementsCount.get() + enforcerService.currentStatus.removedAttachedJars.get()

                    if (changeCount > 0) {
                        MceNotification.showInfo(project, EnforcerBundle.message("listener.mavenReload.info.fixedDependencies", changeCount))
                    }
                } catch (e: Exception) {
                    logger.error(EnforcerBundle.message("listener.mavenReload.error.enforcement"))
                    MceNotification.showError(project, EnforcerBundle.message("listener.mavenReload.error.enforcementWithMessage", e.message?:""))
                    throw e
                }
            }
        })
    }
}