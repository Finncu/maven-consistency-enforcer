package dev.silverhorn.fca.maven_consistency_enforcer.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.ui.ClickListener
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.vcsUtil.showAbove
import dev.silverhorn.fca.maven_consistency_enforcer.service.EnforcerService
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsStateService
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.event.MouseEvent
import javax.swing.*

class MceStatusBarWidget(private val project: Project) : CustomStatusBarWidget, StatusBarWidget {

    companion object {
        const val ID = "MceStatusBarWidget"
    }

    // Wir bauen das UI-Element komplett selbst auf
    private val label = JBLabel("MCE Aktiv").apply {
////        icon = AllIcons.General.RunWithCoverage
////        icon = AllIcons.General.Beta
////        icon = AllIcons.Ide.UpDown
////        icon = AllIcons.Ide.SharedScope +++
////        icon = AllIcons.Actions.Attach
////        icon = AllIcons.Actions.ForceRefresh +-
////        icon = AllIcons.Actions.Collapseall +
////        icon = AllIcons.Actions.DependencyAnalyzer +++
////        icon = AllIcons.Actions.Uninstall +++
////        icon = AllIcons.Actions.Install ++
////        icon = AllIcons.Actions.Lightning ++++
//        icon = AllIcons.Modules.UnloadedModule
////        icon = AllIcons.Modules.SourceRoot +++++++++
//        icon = AllIcons.Scope.Production
////        icon = AllIcons.FileTypes.Manifest -
////        icon = AllIcons.Gutter.OverridenMethod +++
        icon = AllIcons.Gutter.WriteAccess // +++++++
        border = JBUI.Borders.empty(0, 4)

        // Ein nativer, unverwüstlicher Swing-Klick-Listener
        object : ClickListener() {
            override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
                try {
                    val popup = createStatusPopup()
                    // Zeigt das Popup exakt an der geklickten Maus-Koordinate (100% verlässlich)
//                    popup.show(RelativePoint(event))
                    popup.showAbove(this@MceStatusBarWidget.component)
                } catch (e: Throwable) {
                    Messages.showErrorDialog(
                        project,
                        "Fehler beim Öffnen des Status-Popups:\n${e.message}",
                        "MCE Widget Crash"
                    )
                }
                return true
            }
        }.installOn(this)
    }

    // Wird manuell aufgerufen, um den Text zu aktualisieren
    fun updateLabelText() {
        if (project.isDisposed) return
        try {
            val service = project.getService(EnforcerService::class.java)
            val total = service.currentStatus.removedAttachedJars.get()
            label.text = if (total > 0) "MCE | $total" else "MCE active"
            label.repaint()
        } catch (e: Exception) {
            label.text = "MCE Error"
        }
    }

    override fun ID(): String = ID

    override fun getComponent(): JComponent {
        updateLabelText()
        return label
    }

    override fun install(statusBar: StatusBar) {
        updateLabelText()
    }

    private fun createStatusPopup(): com.intellij.openapi.ui.popup.JBPopup {
        val service = project.getService(EnforcerService::class.java)
        val status = service.currentStatus

        val settingsService = project.getService(EnforcerSettingsStateService::class.java)
        val isEnabled = settingsService?.state?.isEnabled ?: true
        val forceLocalModules = settingsService?.state?.forceLocalModules ?: true

        val mainPanel = JPanel(BorderLayout(0, 10)).apply {
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        }

        val headerPanel = JPanel(BorderLayout())
        headerPanel.add(JBLabel("Maven Consistency Enforcer").apply {
            font = font.deriveFont(java.awt.Font.BOLD, font.size + 2f)
        }, BorderLayout.WEST)

        val toolbarPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0))
        val rerunButton = JButton(AllIcons.Actions.Refresh).apply {
            toolTipText = "trigger manually"
            isBorderPainted = false
            isContentAreaFilled = false
            addActionListener {
                ApplicationManager.getApplication().executeOnPooledThread {
                    service.runFullConsistencyCheck()
                }
            }
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
//        val settingsButton = JButton(AllIcons.General.GearPlain).apply {
//            toolTipText = "Einstellungen öffnen"
//            isBorderPainted = false
//            isContentAreaFilled = false
//            addActionListener {
//                ShowSettingsUtil.getInstance().showSettingsDialog(project, EnforcerSettingsConfigurable::class.java)
//            }
//        }
        toolbarPanel.add(rerunButton)
//        toolbarPanel.add(settingsButton)
        headerPanel.add(toolbarPanel, BorderLayout.EAST)
        mainPanel.add(headerPanel, BorderLayout.NORTH)

        val contentPanel = JPanel(BorderLayout(0, 8))

        val metricsPanel = JPanel(GridLayout(0, 2, 10, 4))
        metricsPanel.add(JBLabel("plugin:"))
        metricsPanel.add(JBCheckBox("active", isEnabled).apply {
            addActionListener {
                settingsService?.state?.isEnabled = this.isSelected
            }
        })
        metricsPanel.add(JBLabel("enforce module usage:"))
        metricsPanel.add(JBCheckBox("active", forceLocalModules).apply {
            addActionListener {
                settingsService?.state?.forceLocalModules = this.isSelected
            }
        })
        metricsPanel.add(JBLabel("checked modules:"))
        metricsPanel.add(JBLabel(status.checkedModules.get().toString()))
        metricsPanel.add(JBLabel("ignored modules:"))
        metricsPanel.add(JBLabel(status.ignoredModules.get().toString()))
        metricsPanel.add(JBLabel("removed attached jars:"))
        metricsPanel.add(JBLabel("${status.removedAttachedJars.get()}"))
        metricsPanel.add(JBLabel("enforced module usage:"))// flashcast - sollte noch überarbeitet werden
        metricsPanel.add(JBLabel(status.enforcementsCount.get().toString()))
        metricsPanel.add(JBLabel("processed dependencies:"))
        metricsPanel.add(JBLabel("${status.processedLibraries}"))
        metricsPanel.add(JBLabel("last run:"))
        metricsPanel.add(JBLabel("${status.lastUpdated} (${status.durationMs} ms)"))

        contentPanel.add(metricsPanel, BorderLayout.CENTER)
        mainPanel.add(contentPanel, BorderLayout.CENTER)

        return JBPopupFactory.getInstance()
            .createComponentPopupBuilder(mainPanel, null)
            .setMovable(true)
            .setRequestFocus(true)
            .setCancelOnClickOutside(true)
            .createPopup()
    }
}