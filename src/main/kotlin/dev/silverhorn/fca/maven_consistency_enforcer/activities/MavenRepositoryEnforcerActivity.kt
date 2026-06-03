package dev.silverhorn.fca.maven_consistency_enforcer.activities

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.Messages
import dev.silverhorn.fca.maven_consistency_enforcer.EnforcerBundle
import dev.silverhorn.fca.maven_consistency_enforcer.notifications.MceNotification
import dev.silverhorn.fca.maven_consistency_enforcer.service.EnforcerService
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsState
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsStateService
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.io.File

class MavenRepositoryEnforcerActivity : ProjectActivity {
    private val log = logger<MavenRepositoryEnforcerActivity>()
    private val progressManager: ProgressManager by lazy { ProgressManager.getInstance() }

    private fun chooseInitialHealthCheck(project: Project, state: EnforcerSettingsState): Boolean {
        var res :Int? = null
        ApplicationManager.getApplication().invokeAndWait {
            res = Messages.showYesNoDialog(
                project,
                EnforcerBundle.message("activity.mavenRepositoryEnforcer.initialHealthCheck.message"),
                EnforcerBundle.message("activity.mavenRepositoryEnforcer.initialHealthCheck.title"),
                Messages.getQuestionIcon()
            )
        }
        if  (res == null)
            throw IllegalStateException("Bad things happened")
        if (res != Messages.CANCEL)
            return (Messages.YES == res).apply {
                state.runInitialHealthCheck = this
            }
        else return false
    }

    override suspend fun execute(project: Project) {

        progressManager.run(object : Task.Backgroundable(
            project,
            EnforcerBundle.message("activity.mavenRepositoryEnforcer.brokenRepoConfig.syncing"),
            false
        ) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                try {
                    val mavenManager = MavenProjectsManager.getInstance(project) ?: return
                    val mavenSettings = mavenManager.generalSettings

                    val expectedRepoPath = mavenSettings.localRepository ?: return
                    val expectedCanonical = File(expectedRepoPath).canonicalPath

                    // Das ist der Standard-Fallback-Pfad, in den IntelliJ wegen des Bugs rutscht
                    val defaultM2Path = File(System.getProperty("user.home"), ".m2/repository").canonicalPath

                    // Wenn das erwartete Repository ohnehin das Default-Verzeichnis ist, liegt der Bug hier nicht vor
                    if (expectedCanonical == defaultM2Path) return
                    val stateService: EnforcerSettingsStateService = project.service()

                    if (!(stateService.state.runInitialHealthCheck ?: chooseInitialHealthCheck(
                            project,
                            stateService.state
                        ))
                    )
                        return

                    val libraryTable = project.service<ProjectLibraryTable>()
                    var internalClasspathIsBuggy = false

                    for (library in libraryTable.libraries) {
                        // Wir prüfen die URLs der Classes der Libraries
                        val urls = library.getUrls(OrderRootType.CLASSES)
                        for (url in urls) {
                            if (!url.contains(expectedCanonical)) {
                                internalClasspathIsBuggy = true
                                MceNotification.showInfo(
                                    project,
                                    EnforcerBundle.message("activity.mavenRepositoryEnforcer.brokenRepoConfig.info")
                                )
                                break
                            }
                        }
                        if (internalClasspathIsBuggy) break
                    }

                    // Wenn der Classpath korrupt ist, triggern wir gezielt den Reimport für die Maven-Projekte
                    if (internalClasspathIsBuggy) {
                        log.warn("IntelliJ Bug IDEA-377511 detected")

                        val mavenProjects = mavenManager.projects
                        if (mavenProjects.isNotEmpty()) {
//                            mavenManager.updateAllMavenProjects(MavenSyncSpec.incremental("smee", false))
                             mavenManager.scheduleImportAndResolve()
                        }

                        MceNotification.showInfo(
                            project,
                            EnforcerBundle.message("activity.mavenRepositoryEnforcer.brokenRepoConfig.fixCompleted")
                        )
                        val enforcerService = project.service<EnforcerService>()
                        if (null == project.service<EnforcerService>().currentStatus.lastUpdated)
                            enforcerService.runConsistencyEnforcement()
                    }
                } catch (e: Exception) {
                    MceNotification.showError(
                        project,
                        EnforcerBundle.message("listener.mavenReload.error.enforcementWithMessage", e.message ?: "")
                    )
                    throw e
                }
            }
        })

    }
}
