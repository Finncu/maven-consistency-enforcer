package dev.silverhorn.fca.maven_consistency_enforcer.settings.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import dev.silverhorn.fca.maven_consistency_enforcer.EnforcerBundle
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsStateService

class MceTogglePluginAction : ToggleAction(
    EnforcerBundle.message("action.MceTogglePluginAction.text"),
    EnforcerBundle.message("action.MceTogglePluginAction.description"),
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
    EnforcerBundle.message("action.MceToggleForceModulesAction.text"),
    EnforcerBundle.message("action.MceToggleForceModulesAction.description"),
    null
) {
    override fun isSelected(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        val settingsService = project.service<EnforcerSettingsStateService>()
        return settingsService.state.enforceModuleLinking?:false
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        val settingsService = project.service<EnforcerSettingsStateService>()
        settingsService.state.enforceModuleLinking = state
    }
}

class MceToggleRepoConfigHealthAction : ToggleAction(
    EnforcerBundle.message("action.MceToggleRepoConfigHealthAction.text"),
    EnforcerBundle.message("action.MceToggleRepoConfigHealthAction.description"),
    null
) {
    override fun isSelected(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        val settingsService = project.service<EnforcerSettingsStateService>()
        return settingsService.state.runInitialHealthCheck?:false
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        val settingsService = project.service<EnforcerSettingsStateService>()
        settingsService.state.runInitialHealthCheck = state
    }
}