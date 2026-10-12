package dev.silverhorn.fca.maven_consistency_enforcer.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import dev.silverhorn.fca.maven_consistency_enforcer.EnforcerBundle
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsState
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsStateService
import kotlin.reflect.KMutableProperty1

@Service(Service.Level.PROJECT)
class PopupService(private val project: Project) {

    private val logger = Logger.getInstance(PopupService::class.java)
    private val settings: EnforcerSettingsStateService by lazy { project.service() }

    enum class PopupType(
        public val title: String,
        public val message: String,
        public val property: KMutableProperty1<EnforcerSettingsState, Boolean?>
    ) {
        INITIAL_HEALTH_CHECK(
            "activity.mavenRepositoryEnforcer.initialHealthCheck.title",
            "activity.mavenRepositoryEnforcer.initialHealthCheck.message", EnforcerSettingsState::runInitialHealthCheck
        ),
        POST_RELOAD_PIPELINE(
            "pipeline.popup.postReLoadPipeline.title",
            "pipeline.popup.postReLoadPipeline.message",
            EnforcerSettingsState::enablePostReloadPipeline
        ),
        COMPILE_PIPELINE_STEP(
            "pipeline.popup.compileSteps.title",
            "pipeline.popup.compileSteps.message",
            EnforcerSettingsState::enableCompileStep
        )
    }

    fun choose(popupType: PopupType): Boolean {
        var res: Int? = null
        ApplicationManager.getApplication().invokeAndWait {
            res = Messages.showYesNoDialog(
                project,
                EnforcerBundle.message(popupType.message),
                EnforcerBundle.message(popupType.title),
                Messages.getQuestionIcon()
            )
        }
        if (res == null) throw IllegalStateException(EnforcerBundle.message("activity.mavenRepositoryEnforcer.badState"))
        return if (res != Messages.CANCEL) (Messages.YES == res).apply {
            popupType.property.set(settings.state, this)
        }
        else false
    }
}
