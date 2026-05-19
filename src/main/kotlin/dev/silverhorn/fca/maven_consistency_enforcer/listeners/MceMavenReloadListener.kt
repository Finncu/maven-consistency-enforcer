package dev.silverhorn.fca.maven_consistency_enforcer.listeners

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
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

        MceNotification.showInfo(project, "Maven Reload detected. MCE is checking consistency...")

        progressManager.run(object : Task.Backgroundable(project, "MCE: Enforcing Module Consistency", false) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                try {
                    indicator.text = "MCE: Restructuring module dependencies..."
                    indicator.isIndeterminate = true
                    var changeCount = 0

                    // Übergabe der Einstellungen an den Service
                    changeCount += if (settings.state.forceLocalModules)
                        enforcerService.runFullConsistencyCheck()
                    else enforcerService.cleanupAttachedJars()

                    if (changeCount > 0) {
                        MceNotification.showInfo(project, "MCE: Fixed $changeCount dependencies")
                    }
                } catch (e: Exception) {
                    logger.error("MCE: Error during consistency enforcement", e)
                    MceNotification.showError(project, "MCE: Error during enforcement - ${e.message}")
                }
            }
        })
    }
}