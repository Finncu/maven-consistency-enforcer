package dev.silverhorn.fca.maven_consistency_enforcer.activities

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import com.intellij.openapi.ui.popup.JBPopupFactory
import dev.silverhorn.fca.maven_consistency_enforcer.EnforcerBundle
import dev.silverhorn.fca.maven_consistency_enforcer.notifications.MceNotification
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsState
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsStateService
import kotlin.coroutines.resume
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.io.File

class MavenRepositoryEnforcerActivity : ProjectActivity {
    private val log = logger<MavenRepositoryEnforcerActivity>()
    private val progressManager: ProgressManager by lazy { ProgressManager.getInstance() }

    // Zeigt ein einfaches Auswahl-List-Popup mit Beschreibungen und liefert den Index der Auswahl zurück.
    private suspend fun chooseMavenAction(project: Project): Int? {
        val options = listOf(
            EnforcerBundle.message("activity.mavenRepositoryEnforcer.chooseAction.incremental"),
            EnforcerBundle.message("activity.mavenRepositoryEnforcer.chooseAction.forceRescan"),
            EnforcerBundle.message("activity.mavenRepositoryEnforcer.chooseAction.scheduleImport"),
        )

        return suspendCancellableCoroutine { cont ->
            ApplicationManager.getApplication().invokeLater {
                val popup = JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(options)
                    .setTitle(EnforcerBundle.message("activity.mavenRepositoryEnforcer.chooseAction.title"))
                    .setItemChosenCallback { choice ->
                        cont.resume(options.indexOf(choice))
                    }
                    .createPopup()

                // Popup zentriert in der aktuellen Projekt-Window-Umgebung anzeigen
                popup.showCenteredInCurrentWindow(project)

                cont.invokeOnCancellation {
                    popup.cancel()
                }
            }
        }
    }

    private suspend fun chooseInitialHealthCheck(project: Project, state: EnforcerSettingsState): Boolean {
        return withContext(Dispatchers.EDT) {
            val res = Messages.showYesNoDialog(
                project,
                EnforcerBundle.message("activity.mavenRepositoryEnforcer.initialHealthCheck.message"),
                EnforcerBundle.message("activity.mavenRepositoryEnforcer.initialHealthCheck.title"),
                Messages.getQuestionIcon()
            )
            if (res != Messages.CANCEL)
                return@withContext (Messages.YES == res).let {
                    state.runInitialHealthCheck = it; return@withContext it
                }
            else return@withContext false
        }
    }

    override suspend fun execute(project: Project) {
        withContext(Dispatchers.EDT) {
            val mavenManager = MavenProjectsManager.getInstance(project) ?: return@withContext
            val mavenSettings = mavenManager.generalSettings

            // Das ist der Pfad, den IntelliJ eigentlich nutzen SOLLTE (z.B. aus deiner Settings.xml)
            val expectedRepoPath = mavenSettings.localRepository ?: return@withContext
            val expectedCanonical = File(expectedRepoPath).canonicalPath

            // Das ist der Standard-Fallback-Pfad, in den IntelliJ wegen des Bugs rutscht
            val defaultM2Path = File(System.getProperty("user.home"), ".m2/repository").canonicalPath

            // Wenn das erwartete Repository ohnehin das Default-Verzeichnis ist, liegt der Bug hier nicht vor
            if (expectedCanonical == defaultM2Path) return@withContext
            val stateService: EnforcerSettingsStateService = project.service()

            if (!(stateService.state.runInitialHealthCheck ?: chooseInitialHealthCheck(
                    project,
                    stateService.state
                ))
            )
                return@withContext

            // Jetzt prüfen wir, ob die aktuell im Projekt registrierten Libraries fälschlicherweise auf ~/.m2 zeigen
            val libraryTable = ProjectLibraryTable.getInstance(project)
            var internalClasspathIsBuggy = false

            for (library in libraryTable.libraries) {
                // Wir prüfen die URLs der Classes der Libraries
                val urls = library.getUrls(OrderRootType.CLASSES)
                for (url in urls) {
                    // Falls eine URL das Standard-.m2 enthält, obwohl wir ein eigenes nutzen: Bug getroffen!
                    if (!url.contains(expectedCanonical)) {
                        internalClasspathIsBuggy = true
                        MceNotification.showInfo(project, EnforcerBundle.message("activity.mavenRepositoryEnforcer.brokenRepoConfig.info"))
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
//                    mavenManager.updateAllMavenProjects(MavenSyncSpec.incremental("smee", false))
                    mavenManager.scheduleImportAndResolve()
                }

                MceNotification.showInfo(project, EnforcerBundle.message("activity.mavenRepositoryEnforcer.brokenRepoConfig.fixCompleted"))
            }
        }
    }
}
