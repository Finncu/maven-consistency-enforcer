package dev.silverhorn.fca.maven_consistency_enforcer.settings

import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager

class EnforcerSearchableOptionContributor : SearchableOptionContributor() {

    override fun processOptions(processor: SearchableOptionProcessor) {
        // Die ID muss exakt mit der getId() deines EnforcerSettingsConfigurable übereinstimmen
        val configurableId = "dev.silverhorn.fca.maven_consistency_enforcer.settings"
        val settingsPath = "Tools > Maven Consistency Enforcer"

        // Helper, um sicher an den State des aktuell geöffneten Projekts zu kommen
        val getSettingsState = {
            ProjectManager.getInstance().openProjects.firstOrNull()
                ?.service<EnforcerSettingsStateService>()?.state
        }

        // 1. Option für: Global aktivieren/deaktivieren (isEnabled)
        val enablePluginOption = object : BooleanOptionDescription(
            "Maven Consistency Enforcer: Enable Plugin",
            configurableId
        ) {
            override fun isOptionEnabled(): Boolean = getSettingsState()?.isEnabled ?: true
            override fun setOptionState(enabled: Boolean) {
                getSettingsState()?.isEnabled = enabled
            }
        }

        // 2. Option für: Erzwungene Modulnutzung (forceLocalModules)
        val forceModulesOption = object : BooleanOptionDescription(
            "Maven Consistency Enforcer: Enable Enforcement of Local Module Usage",
            configurableId
        ) {
            override fun isOptionEnabled(): Boolean = getSettingsState()?.forceLocalModules ?: true
            override fun setOptionState(enabled: Boolean) {
                getSettingsState()?.forceLocalModules = enabled
            }
        }

        // Dem Prozessor hinzufügen, damit sie in Doppel-Shift indiziert werden
        processor.addOptions(enablePluginOption.option!!, null, enablePluginOption.option, configurableId, settingsPath, true)
        processor.addOptions(forceModulesOption.option!!, null, forceModulesOption.option, configurableId, settingsPath, true)
    }
}