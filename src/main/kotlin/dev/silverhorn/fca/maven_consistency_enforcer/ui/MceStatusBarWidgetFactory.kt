package dev.silverhorn.fca.maven_consistency_enforcer.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class MceStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = MceStatusBarWidget.ID

    override fun getDisplayName(): String = "Maven Consistency Enforcer"

    override fun isAvailable(project: Project): Boolean = true

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

    // GANZ WICHTIG: Hier muss jedes Mal 'return MceStatusBarWidget(project)' stehen.
    // Darf niemals gecached oder als Singleton gehalten werden!
    override fun createWidget(project: Project): StatusBarWidget {
        return MceStatusBarWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }
}