package dev.silverhorn.fca.maven_consistency_enforcer.service

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.util.containers.stream
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.util.LinkedList

@Service(Service.Level.PROJECT)
class EnforcerService(private val project: Project) {

    private val logger = Logger.getInstance(EnforcerService::class.java)
    private val consistencyChanges = mutableListOf<String>()
    private val moduleManager: ModuleManager by lazy { project.service() }
    private val mavenProjectsManager: MavenProjectsManager by lazy { project.service() }

    private val mvnPattern by lazy { Regex("^Maven: ?[^ :]+:([^ :]+)") }
    private val gavPattern by lazy { Regex("[^ :]+:([^ :]+):[^ :]+.*") }

    fun runFullConsistencyCheck(): Int {
        consistencyChanges.clear()
        var totalChanges = 0

        logger.info("MCE: Starting full consistency check for ${moduleManager.modules.size} modules")

        totalChanges += enforceModulesConsistency()

        totalChanges += cleanupAttachedJars()

        logger.info("MCE: Consistency check completed. Total replacements: $totalChanges")
        return totalChanges
    }

    private fun cleanupAttachedJars(): Int {
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

    private fun enforceModulesConsistency(): Int {
        var changesCount = 0
        val moduleMap = moduleManager.modules.mapNotNull { module ->
            val mavenProject = mavenProjectsManager.findProject(module) ?: return@mapNotNull null
            val artifactId = mavenProject.mavenId.artifactId ?: return@mapNotNull null
            artifactId to module
        }.toMap()
        for (module in moduleManager.modules)
            ModuleRootModificationUtil.updateModel(module) { model ->
                val replacements = model.orderEntries.filterIsInstance<LibraryOrderEntry>().mapNotNull { entry ->
                    entry.libraryName?.let { moduleMap[this.extractArtifactId(it)] }?.let {
                        entry to it
                    }
                }
                for ((libEntry, module) in replacements) {
                    model.removeOrderEntry(libEntry)
                    model.addModuleOrderEntry(module)
                    changesCount++
                }
            }
        return changesCount
    }

    private fun extractArtifactId(libraryName: String): String? {
        val gavRes: String? =
            mvnPattern.find(libraryName)?.groupValues[1]
                ?: gavPattern.find(libraryName)?.groupValues[1]
        if (gavRes == null)
            logger.warn("$libraryName doesnt match GAV-C")
        return gavRes
    }
}

