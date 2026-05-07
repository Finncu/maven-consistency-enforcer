package dev.silverhorn.fca.maven_consistency_enforcer.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.vfs.VirtualFile
import dev.silverhorn.fca.maven_consistency_enforcer.notifications.MceNotification
import dev.silverhorn.fca.maven_consistency_enforcer.service.EnforcerService
import java.io.IOException

/**
 * PurgeArtifactAction: Manuelle Säuberung von stalen Maven-Artifacts.
 *
 * Diese Action wird vom Benutzer manuell getriggert (typischerweise über ein
 * Toolbar-Button oder Kontextmenü) und entfernt das target/ oder build/ Verzeichnis
 * des aktuellen Moduls, sowie erzeugt eine neue Konsistenz-Prüfung.
 *
 * **Workflow:**
 * 1. Benutzer klickt auf "MCE Purge Artifacts" Button
 * 2. Target/Build-Verzeichnis wird rekursiv gelöscht
 * 3. Maven-Caches werden invalidiert (optional)
 * 4. EnforcerService führt neue Consistency-Prüfung durch
 * 5. Projekt wird triggert, neu zu kompilieren
 *
 * **Anwendungsfall:**
 * Wenn IntelliJ "steckt" oder veraltete JARs hartnäckig im Classpath bleiben,
 * kann diese Action ein hartes "Reset" durchführen.
 */
class PurgeArtifactAction : AnAction("MCE: Purge Stale Artifacts", "Delete target/build directories and enforce consistency", null) {

    private val logger = Logger.getInstance(PurgeArtifactAction::class.java)

    /**
     * Wird aufgerufen, wenn der Benutzer die Action aktiviert.
     *
     * @param event Das ActionEvent mit Kontext-Informationen (Projekt, Modul, etc.)
     */
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: run {
            logger.warn("MCE: No project context available")
            return
        }

        logger.info("MCE: Purge Artifacts action triggered")
        MceNotification.showInfo(project, "MCE: Starting artifact purge...")

        // Starte den Purge in einem Background-Task
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "MCE: Purging Artifacts", true) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                try {
                    indicator.text = "MCE: Locating target/build directories..."
                    indicator.isIndeterminate = true

                    val projectDir = project.baseDir
                    var deletedCount = 0
                    var errors = 0

                    // Versuche, alle target/ und build/ Verzeichnisse zu löschen
                    deletedCount += deleteDirectoryRecursively(projectDir, "target", indicator)
                    deletedCount += deleteDirectoryRecursively(projectDir, "build", indicator)

                    indicator.text = "MCE: Enforcing consistency..."

                    // Führe einen neuen Konsistenz-Check durch
                    val enforcerService = EnforcerService.getInstance(project)
                    val changeCount = enforcerService.runFullConsistencyCheck()

                    val message = if (changeCount > 0) {
                        "MCE: Purged $deletedCount directory(ies) and fixed $changeCount dependencies"
                    } else {
                        "MCE: Purged $deletedCount directory(ies). All modules now consistent ?"
                    }

                    MceNotification.showInfo(project, message)
                    logger.info("MCE: Purge completed. Deleted: $deletedCount, Fixed: $changeCount")

                } catch (e: Exception) {
                    logger.error("MCE: Error during artifact purge", e)
                    MceNotification.showError(project, "MCE: Purge failed - ${e.message}")
                }
            }

            override fun onCancel() {
                logger.info("MCE: Artifact purge cancelled by user")
                MceNotification.showWarning(project, "MCE: Purge was cancelled")
            }
        })
    }

    /**
     * Löscht alle Verzeichnisse mit einem bestimmten Namen rekursiv.
     *
     * Sucht alle Verzeichnisse mit dem angegebenen Namen (z.B. "target", "build")
     * und löscht diese inklusive ihres gesamten Inhalts. Erstreckt sich über alle
     * Module (Subdirectories) des Projekts.
     *
     * @param rootDir Das Root-Verzeichnis der Suche
     * @param directoryName Der Name der zu löschenden Verzeichnisse
     * @param indicator Progress-Indicator für Benutzer-Feedback
     * @return Anzahl der gelöschten Verzeichnisse
     */
    private fun deleteDirectoryRecursively(
        rootDir: VirtualFile,
        directoryName: String,
        indicator: com.intellij.openapi.progress.ProgressIndicator
    ): Int {
        var deletedCount = 0

        try {
            // Suche rekursiv nach allen target/build-Verzeichnissen
            for (child in rootDir.children) {
                indicator.checkCanceled()

                when {
                    child.isDirectory && child.name == directoryName -> {
                        // Gefunden - versuche zu löschen
                        indicator.text = "MCE: Deleting ${child.path}..."
                        try {
                            child.delete(this)
                            deletedCount++
                            logger.debug("MCE: Deleted directory: ${child.path}")
                        } catch (e: IOException) {
                            logger.warn("MCE: Failed to delete directory '${child.path}': ${e.message}")
                        }
                    }
                    child.isDirectory -> {
                        // Rekursion in Subdirectories (aber nicht in .git, .idea, etc.)
                        if (!child.name.startsWith(".")) {
                            deletedCount += deleteDirectoryRecursively(child, directoryName, indicator)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("MCE: Error during recursive directory deletion", e)
        }

        return deletedCount
    }

    /**
     * Bestimmt, ob diese Action im aktuellen Kontext sichtbar und aktiviert sein soll.
     *
     * Die Action ist nur sichtbar, wenn ein Projekt geöffnet ist.
     *
     * @param event Das ActionEvent mit Kontext
     */
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null
    }
}

