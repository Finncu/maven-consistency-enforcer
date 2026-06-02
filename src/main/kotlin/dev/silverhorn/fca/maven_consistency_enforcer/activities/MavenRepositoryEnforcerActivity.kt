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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import com.intellij.openapi.ui.popup.JBPopupFactory
import dev.silverhorn.fca.maven_consistency_enforcer.notifications.MceNotification
import dev.silverhorn.fca.maven_consistency_enforcer.service.EnforcerService
import kotlin.coroutines.resume
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.io.File

class MavenRepositoryEnforcerActivity : ProjectActivity {
    private val log = logger<MavenRepositoryEnforcerActivity>()
    private val progressManager: ProgressManager by lazy { ProgressManager.getInstance() }

    // Zeigt ein einfaches Auswahl-List-Popup mit Beschreibungen und liefert den Index der Auswahl zurück.
    private suspend fun chooseMavenAction(project: Project): Int? {
            val options = listOf(
            "Inkrementelle Aktualisierung aller Maven-Projekte - versucht nur geänderte POMs zu verarbeiten",
            "Import via Maven-Sync-Konsole starten - startet schnellen Import der Projekte",
            "Alle Projekte erzwingen / alle POMs finden - erzwingt kompletten Rescan",
            "Import & Resolve planen - importiert und loest Abhaengigkeiten im Hintergrund",
                "enf service"
        )

        return suspendCancellableCoroutine { cont ->
            ApplicationManager.getApplication().invokeLater {
                val popup = JBPopupFactory.getInstance()
                    .createPopupChooserBuilder(options)
                    .setTitle("Maven-Aktion auswählen")
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

    override suspend fun execute(project: Project) {
        withContext(Dispatchers.Default) {
            val mavenManager = MavenProjectsManager.getInstance(project) ?: return@withContext
            val enfService = project.service<EnforcerService>()
            val mavenSettings = mavenManager.generalSettings

            // Das ist der Pfad, den IntelliJ eigentlich nutzen SOLLTE (z.B. aus deiner Settings.xml)
            val expectedRepoPath = mavenSettings.localRepository ?: return@withContext
            val expectedCanonical = File(expectedRepoPath).canonicalPath

            // Das ist der Standard-Fallback-Pfad, in den IntelliJ wegen des Bugs rutscht
            val defaultM2Path = File(System.getProperty("user.home"), ".m2/repository").canonicalPath

            // Wenn das erwartete Repository ohnehin das Default-Verzeichnis ist, liegt der Bug hier nicht vor
            if (expectedCanonical == defaultM2Path) return@withContext

            // Jetzt prüfen wir, ob die aktuell im Projekt registrierten Libraries fälschlicherweise auf ~/.m2 zeigen
            val libraryTable = ProjectLibraryTable.getInstance(project)
            var internalClasspathIsBuggy = false

            for (library in libraryTable.libraries) {
                // Wir prüfen die URLs der Classes der Libraries
                val urls = library.getUrls(OrderRootType.CLASSES)
                for (url in urls) {
                    // Falls eine URL das Standard-.m2 enthält, obwohl wir ein eigenes nutzen: Bug getroffen!
                    if (url.contains(".m2/repository") && !url.contains(expectedCanonical)) {
                        internalClasspathIsBuggy = true
                        break
                    }
                }
                if (internalClasspathIsBuggy) break
            }

            // Wenn der Classpath korrupt ist, triggern wir gezielt den Reimport für die Maven-Projekte
            if (internalClasspathIsBuggy) {
                log.warn("IntelliJ Bug IDEA-377511 erkannt: Classpath verweist auf falsches M2-Repository. Triggere gezielten Maven-Import...")


                // Zeige ein Auswahl-Popup an (nur eine Auswahl möglich) mit treffenden Beschreibungen
                val mavenProjects = mavenManager.projects
                if (mavenProjects.isNotEmpty()) {
                    // Popup muss auf EDT gezeigt werden; wir suspendieren, bis der Nutzer eine Auswahl trifft
                    val choice = chooseMavenAction(project)

                    when (choice) {
                        0 -> mavenManager.updateAllMavenProjects(MavenSyncSpec.incremental("smee", false))
                        1 -> mavenManager.syncConsole.startImport(false)
                        2 -> mavenManager.forceUpdateAllProjectsOrFindAllAvailablePomFiles()
                        3 -> mavenManager.scheduleImportAndResolve()
                        4 -> progressManager.run(object : Task.Backgroundable(project, "MCE: Enforcing Module Consistency", false) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                try {

                    enfService.fixMavenRepository()

                } catch (e: Exception) {
                    MceNotification.showError(project, "MCE: Error during enforcement - ${e.message}")
                    throw e
                }
            }
        })
                        else -> log.info("Keine Maven-Aktion ausgewählt")
                    }
                }
            }
        }
    }
}
