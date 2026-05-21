package dev.silverhorn.fca.maven_consistency_enforcer.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class MceStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = MceStatusBarWidget.ID

    override fun getDisplayName(): String = "Maven Consistency Enforcer"

    override fun isAvailable(project: Project): Boolean = true

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget {
        return MceStatusBarWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        // HIER IST DER FIX FÜRS VERSCHWINDEN: Tötet das alte Widget restlos ab,
        // damit IntelliJ den Platz wieder für ein neues freigibt!
        Disposer.dispose(widget)
    }
}