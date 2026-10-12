package dev.silverhorn.fca.maven_consistency_enforcer.settings.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import dev.silverhorn.fca.maven_consistency_enforcer.pipeline.PostReloadPipelineService

/**
 * Manuelle Action zum sofortigen Ausf?hren der Post-Reload-Pipeline
 * (Compile + Run-Configurations), unabh?ngig von einem Maven-Reload.
 */
class MceRunPipelineAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.service<PostReloadPipelineService>().runPipelineManually()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

