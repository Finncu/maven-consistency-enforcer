package dev.silverhorn.fca.maven_consistency_enforcer.activities

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import dev.silverhorn.fca.maven_consistency_enforcer.EnforcerBundle
import dev.silverhorn.fca.maven_consistency_enforcer.notifications.MceNotification
import dev.silverhorn.fca.maven_consistency_enforcer.service.EnforcerService
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsState
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsStateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.buildtool.MavenSyncSpec
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.io.File
import kotlin.coroutines.resume

class MavenRepositoryEnforcerActivity : ProjectActivity {
    private val log = logger<MavenRepositoryEnforcerActivity>()

    override suspend fun execute(project: Project) {
        project.service<EnforcerService>().runMavenConfigurationCheck()
    }

}
