package dev.silverhorn.fca.maven_consistency_enforcer.listeners

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbService
import dev.silverhorn.fca.maven_consistency_enforcer.service.EnforcerService
import dev.silverhorn.fca.maven_consistency_enforcer.notifications.MceNotification
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.idea.maven.project.MavenProject

/**
 * MceMavenReloadListener: Lauscht auf Maven-Import-Ereignisse in IntelliJ.
 *
 * Dieser Listener wird automatisch getriggert, wenn:
 * 1. Der Benutzer explizit "Maven -> Reload Project" aufruft
 * 2. Eine pom.xml-Datei geändert wird und IntelliJ diese neu einliest
 * 3. Ein neues Modul hinzugefügt wird
 *
 * Nach dem Reload wird die MCE-Konsistenzprüfung ausgelöst, um sicherzustellen,
 * dass die neu importierten Module direkte Abhängigkeiten verwenden und keine
 * veralteten JARs aus dem .m2-Repository mitgeschleppt werden.
 *
 * **Workflow:**
 * 1. Maven-Import beendet -> importFinished() wird aufgerufen
 * 2. UX: Notification "Maven Reload detected..."
 * 3. DumbService: Warte auf Indexing-Fertigstellung
 * 4. Enforcement: Starte vollständigen Konsistenz-Check
 * 5. UX: Benachrichtige Benutzer über Ergebnisse
 */
class MceMavenReloadListener(private val project: Project) : MavenImportListener {

    private val logger = Logger.getInstance(MceMavenReloadListener::class.java)
    private val enforcerService :EnforcerService by lazy { project.service() }
    private val dumbService : DumbService by lazy { project.service() }
    private val progressManager : ProgressManager by lazy { ProgressManager.getInstance() }
    /**
     * Wird aufgerufen, wenn Maven das Projekt-Modell aktualisiert hat.
     *
     * Diese Methode wird asynchron nach jedem Maven-Import/Reload aufgerufen.
     * Sie startet den MCE-Konsistenz-Check in einem Background-Task, um die
     * IDE nicht zu blockieren.
     *
     * @param importedProjects Collection der neu importierten Maven-Projekte
     * @param newModules Liste der neu hinzugefügten IDE-Module
     */
    override fun importFinished(importedProjects: Collection<MavenProject>, newModules: List<com.intellij.openapi.module.Module>) {
        if (importedProjects.isEmpty()) {
            logger.debug("MCE: Maven import finished with no projects, skipping consistency check")
            return
        }

        logger.info("MCE: Maven import finished for ${importedProjects.size} project(s). Starting consistency enforcement.")

        // Notifiziere den Benutzer, dass der Enforcer lädt
        MceNotification.showInfo(project, "Maven Reload detected. MCE is checking consistency...")

        // Starte den Enforcer in einem Background-Task mit DumbService-Wrapper
        // Dies stellt sicher, dass:
        // 1. Die IDE nicht blockiert wird
        // 2. Wir warten, bis das Indexing fertig ist
        // 3. Alle Write-Operationen auf das Proj-Modell sauber ausgeführt werden
        startEnforcementTask(project)
    }

    /**
     * Startet den MCE-Enforcement in einem Background-Task.
     *
     * Der Task läuft im Hintergrund und nutzt DumbService.runWhenSmart(),
     * um sicherzustellen, dass die IDE-Indizierung abgeschlossen ist,
     * bevor wir auf das Projekt-Modell schreiben.
     *
     * @param project Das aktuelle Projekt
     */
    private fun startEnforcementTask(project: Project) {
        progressManager.run(object : Task.Backgroundable(project, "MCE: Enforcing Module Consistency", false) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                indicator.text = "MCE: Waiting for IDE to be ready..."
                indicator.isIndeterminate = true

                // Warte, bis die Indizierung fertig ist
                dumbService.runWhenSmart {
                    try {
                        indicator.text = "MCE: Scanning modules for stale dependencies..."

                        // Führe den vollständigen Konsistenz-Check durch
                        val changeCount = enforcerService.runFullConsistencyCheck()

                        // Benachrichtige über die Ergebnisse
                        if (changeCount > 0) {
                            MceNotification.showInfo(project, "MCE: Fixed $changeCount dependencies")
                        } else {
                            MceNotification.showInfo(project, "MCE: All modules consistent ?")
                        }

                        logger.info("MCE: Enforcement completed. Changes: $changeCount")
                    } catch (e: Exception) {
                        logger.error("MCE: Error during consistency enforcement", e)
                        MceNotification.showError(project, "MCE: Error during enforcement - ${e.message}")
                    }
                }
            }
        })
    }
}

