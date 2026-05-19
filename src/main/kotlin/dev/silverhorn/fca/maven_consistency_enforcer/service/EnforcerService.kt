package dev.silverhorn.fca.maven_consistency_enforcer.service

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import java.util.LinkedList

/**
 * EnforcerService: Kernlogik für die Durchsetzung von direkten Modul-zu-Modul-Abhängigkeiten.
 *
 * Diese Service-Klasse scannt alle Module eines Projekts und ersetzt JAR-Abhängigkeiten
 * aus dem lokalen Maven-Repository durch echte Modul-Dependencies, falls das entsprechende
 * Quellmodul im aktuellen Projekt vorhanden ist.
 *
 * **Hauptfunktionen:**
 * - Detection: Identifiziert Library-Entries, die auf .m2/repository verweisen
 * - Mapping: Findet das entsprechende Quellmodul basierend auf Artifact-Koordinaten
 * - Enforcement: Ersetzt gefundene JAR-Abhängigkeiten durch Modul-Dependencies
 * - Reporting: Benachrichtigt über durchgeführte Korrektionen
 */
class EnforcerService(private val project: Project) {

    private val logger = Logger.getInstance(EnforcerService::class.java)
    private val consistencyChanges = mutableListOf<String>()

    /**
     * Führt einen vollständigen Konsistenz-Check für alle Module durch.
     *
     * Diese Methode wird typischerweise nach einem Maven-Reload aufgerufen und scannt
     * alle Module des Projekts auf problematische Library-Abhängigkeiten.
     *
     * @return Anzahl der durchgeführten Korrektionen
     */
    fun runFullConsistencyCheck(): Int {
        consistencyChanges.clear()
        val modules = ModuleManager.getInstance(project).modules
        var totalChanges = 0

        logger.info("MCE: Starting full consistency check for ${modules.size} modules")

        for (module in modules) {
            val changesForModule = enforceModuleConsistency(module)
            totalChanges += changesForModule
        }

        totalChanges += cleanupAttachedJars()

        logger.info("MCE: Consistency check completed. Total replacements: $totalChanges")
        return totalChanges
    }

    /**
     * Entfernt ungenutzte Maven-Project-Libraries aus der Project-Structure.
     *
     * Diese Routine bereinigt Library-Einträge unter "Project Structure > Libraries",
     * die aus dem lokalen Maven-Repository stammen und von keinem Modul mehr referenziert werden.
     *
     * @return Anzahl der gelöschten Project-Libraries
     */
    private fun cleanupAttachedJars(): Int {
        val referencedLibraryNames = ModuleManager.getInstance(project).modules
            .flatMap { module ->
                ModuleRootManager.getInstance(module).orderEntries
                    .filterIsInstance<LibraryOrderEntry>()
                    .mapNotNull { it.libraryName }
            }
            .toSet()

        val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
        var removedCount = 0

        WriteAction.run<RuntimeException> {
            val tableModel = libraryTable.modifiableModel

            for (library in libraryTable.libraries) {

                if (library?.name?.contains("ATTACHED-JAR") ?: false) {
                    tableModel.removeLibrary(library)
                    removedCount++
                    consistencyChanges.add("Removed project library: " + library?.name)
                    logger.debug("MCE: Removed unused project library " + library?.name)
                }
            }

            tableModel.commit()
        }

        return removedCount
    }

    /**
     * Durchsetzt die Konsistenz für ein einzelnes Modul.
     *
     * Scannt alle OrderEntries des Moduls und ersetzt Library-Einträge, deren
     * Quellen im lokalen .m2-Repository liegen, durch echte Modul-Dependencies,
     * falls das entsprechende Modul im Projekt existiert.
     *
     * @param module Das Modul, das auf Konsistenz geprüft werden soll
     * @return Anzahl der durchgeführten Ersetzungen für dieses Modul
     */
    private fun enforceModuleConsistency(module: Module): Int {
        var changesCount = 0

        ModuleRootModificationUtil.updateModel(module) { model ->
            val entriesToRemove = mutableListOf<OrderEntry>()
            val entriesToAdd = mutableListOf<Module>()

            // Iteriere über alle OrderEntries und identifiziere problematische JARs
            for (entry in model.orderEntries) {
                if (entry is LibraryOrderEntry) {
                    val sourceModule = findSourceModuleForLibrary(entry)
                    if (sourceModule != null) {
                        entriesToRemove.add(entry)
                        entriesToAdd.add(sourceModule)
                    }
                }
            }

            // Entferne die problematischen JAR-Einträge
            for (entry in entriesToRemove) {
                model.removeOrderEntry(entry)
                changesCount++
                val libName = if (entry is LibraryOrderEntry) entry.libraryName else "unknown"
                logger.debug("Removed JAR library: $libName from module: ${module.name}")
                consistencyChanges.add("Removed JAR library: $libName from module: ${module.name}")
            }

            // Füge die Modul-Abhängigkeiten hinzu
            for (sourceModule in entriesToAdd) {
                model.addModuleOrderEntry(sourceModule)
                logger.debug("Added module dependency: ${sourceModule.name} to module: ${module.name}")
                consistencyChanges.add("Added module dependency: ${sourceModule.name} to module: ${module.name}")
            }

            changesCount > 0
        }

        return changesCount
    }

    /**
     * Findet das entsprechende Quellmodul für eine gegebene Library-Abhängigkeit.
     *
     * Das Mapping erfolgt durch:
     * 1. Prüfung, ob die Library-URL im lokalen .m2-Repository liegt
     * 2. Extraktion der Artifact-Koordinaten (groupId,artifactId, version)
     * 3. Abgleich mit existierenden Modulnamen im Projekt
     *
     * @param libraryEntry Die zu prüfende Library-Abhängigkeit
     * @return Das entsprechende Quellmodul, oder null falls nicht gefunden oder nicht problematisch
     */
    private fun findSourceModuleForLibrary(libraryEntry: LibraryOrderEntry): Module? {
        val library = libraryEntry.library ?: return null
        val urls = LinkedList<String>()
        urls.addAll(library.getUrls(OrderRootType.CLASSES))
        urls.addAll(library.getUrls(OrderRootType.SOURCES))

        // Prüfe, ob mindestens eine URL im .m2-Repository liegt
        val isFromM2 = urls.any { url -> isMavenRepositoryUrl(url) }

        if (!isFromM2) {
            return null
        }

        // Extrahiere Artifact-Informationen aus der JAR-URL oder dem Library-Namen
        val libraryName = libraryEntry.libraryName ?: return null
        val moduleManager = ModuleManager.getInstance(project)

        // Versuche, das Modul anhand des Library-Namens zu finden
        // Typisches Maven-Namensschema: "artifactId" oder "artifactId-version"
        val artifactId = extractArtifactId(libraryName)
        val sourceModule = moduleManager.modules.firstOrNull { module ->
            module.name.equals(artifactId, ignoreCase = true)
        }

        if (sourceModule != null) {
            logger.debug("MCE: Found source module '$sourceModule' for library '$libraryName'")
        }

        return sourceModule
    }

    /**
     * Prüft, ob eine Klassen-URL auf das lokale Maven-Repository verweist.
     *
     * @param url Die zu prüfende Library-URL
     * @return true, wenn die URL auf .m2/repository verweist
     */
    private fun isMavenRepositoryUrl(url: String): Boolean {
        return url.contains("mavenrepository") || url.contains(".m2/repository") || url.contains(".m2\\repository")
    }

    /**
     * Extrahiert die Artifact-ID aus einem Maven-Library-Namen.
     *
     * Entfernt die Version und Classifier aus dem Library-Namen, um die
     * Basis-Artifact-ID zu erhalten. Diese wird dann für das Modul-Matching verwendet.
     *
     * Beispiele:
     * - "my-app-1.0.0" -> "my-app"
     * - "core-service-2.1.0-SNAPSHOT" -> "core-service"
     * - "utils-1.0.0-sources" -> "utils"
     *
     * @param libraryName Der vollständige Maven-Library-Name
     * @return Die extrahierte Artifact-ID
     */
    private fun extractArtifactId(libraryName: String): String? {
        val gavPattern = Regex("([^:]*): ([^:]*):([^:]*):([^:]*)")
        val gavRes : String? = gavPattern.find(libraryName)?.groupValues[3]
        if (gavRes == null)
            logger.warn("$libraryName doesnt match Gav shit")
        return gavRes
    }

    /**
     * Gibt eine Zusammenfassung der durchgeführten Änderungen zurück.
     *
     * @return Liste der durchgeführten Korrektionen
     */
    fun getConsistencyChanges(): List<String> = consistencyChanges.toList()

    companion object {
        /**
         * Ruft den EnforcerService für das aktuelle Projekt ab.
         *
         * @param project Das IntelliJ-Projekt
         * @return Die Service-Instanz für das Projekt
         */
        fun getInstance(project: Project): EnforcerService {
            return project.getService(EnforcerService::class.java)
        }
    }
}

