package dev.silverhorn.fca.maven_consistency_enforcer.settings.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsStateService

class MceTogglePluginAction : ToggleAction(
    "Maven Consistency Enforcer: Enable Plugin",
    "Toggle the automatic Maven consistency enforcements on or off",
    null
) {
    override fun isSelected(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        val settingsService = project.service<EnforcerSettingsStateService>()
        return settingsService.state.isEnabled
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        val settingsService = project.service<EnforcerSettingsStateService>()
        settingsService.state.isEnabled = state
    }
}

class MceToggleForceModulesAction : ToggleAction(
    "Maven Consistency Enforcer: Force Local Module Usage",
    "Toggle enforcement of local module dependencies over Maven artifacts",
    null
) {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val project = e.project
        if (project != null) {
            val settingsService = project.service<EnforcerSettingsStateService>()
            // Wenn das Haupt-Plugin aus ist, wird dieser Schalter in der Suche ausgegraut
            e.presentation.isEnabled = settingsService.state.isEnabled
        }
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        val settingsService = project.service<EnforcerSettingsStateService>()
        return settingsService.state.forceLocalModules
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        val settingsService = project.service<EnforcerSettingsStateService>()
        settingsService.state.forceLocalModules = state
    }
}