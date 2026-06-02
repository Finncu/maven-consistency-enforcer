package dev.silverhorn.fca.maven_consistency_enforcer.activities

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.intellij.openapi.ui.Messages
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.io.File

class MavenRepositoryEnforcerActivity : ProjectActivity {

    private val log = logger<MavenRepositoryEnforcerActivity>()

    override suspend fun execute(project: Project) {
        // Wir wechseln in den Hintergrund-Thread für die Validierung
        withContext(Dispatchers.Default) {
            val mavenManager = MavenProjectsManager.getInstance(project) ?: return@withContext
            val mavenSettings = mavenManager.generalSettings

            // Aktuell konfigurierten Pfad auslesen
            val currentRepoPath = mavenSettings.localRepository

            // Das gewünschte, projektspezifische oder lokale Ziel-Repository definieren
            // TODO: Pfad dynamisch aus den Plugin-Einstellungen oder einem festen Standard auslesen
            val targetRepoPath = "${project.basePath}/.m2/repository"

            if (needsCorrection(currentRepoPath, targetRepoPath)) {
                log.info("Fehlerhaften M2-Pfad erkannt: $currentRepoPath. Erzwinge Wechsel auf: $targetRepoPath")

                // Änderungen an den Settings müssen im Event Dispatch Thread (EDT) via WriteAction erfolgen
//                withContext(Dispatchers.EDT) {
//                    writeAction {
//                        mavenSettings.localRepository = targetRepoPath
//                    }
//                }

                // Trigger einen Reimport der Maven-Zweige, damit die Änderungen greifen
                log.info("Triggere Maven-Reimport nach Pfad-Korrektur.")
                // Kurze Bestätigung beim Nutzer einholen (EDT)

                var case = 5
                when (case) {
                    0 -> mavenManager.updateAllMavenProjects(MavenSyncSpec.incremental("smee", false))
                    1 -> mavenManager.syncConsole.startImport(false)
                    2 -> mavenManager.forceUpdateAllProjectsOrFindAllAvailablePomFiles()
                    else -> null
                }
            }
        }
    }

    /**
     * Prüft, ob der Pfad korrigiert werden muss.
     * Schlägt an, wenn der Pfad leer ist, auf das Standard-User-Verzeichnis zeigt oder schlicht falsch ist.
     */
    private fun needsCorrection(currentPath: String?, targetPath: String): Boolean {
        if (currentPath.isNullOrBlank()) return true

        val currentFile = File(currentPath).canonicalPath
        val targetFile = File(targetPath).canonicalPath

        // Erkennt den typischen IntelliJ-Bug, bei dem auf das Default-User-.m2 zurückgefallen wird
        val defaultM2Path = File(System.getProperty("user.home"), ".m2/repository").canonicalPath

        return currentFile == defaultM2Path || currentFile != targetFile
    }
}