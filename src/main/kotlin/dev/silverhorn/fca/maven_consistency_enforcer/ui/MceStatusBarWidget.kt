package dev.silverhorn.fca.maven_consistency_enforcer.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.Consumer
import dev.silverhorn.fca.maven_consistency_enforcer.service.EnforcerService
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsConfigurable
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsStateService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.event.MouseEvent
import javax.swing.*

class MceStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.MultipleTextValuesPresentation {

    companion object {
        const val ID = "MceStatusBarWidget"
    }

    override fun ID(): String = ID
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
    override fun getTooltipText(): String = "Maven Consistency Enforcer Status anzeigen"
    override fun getIcon(): Icon = IconLoader.getIcon("/META-INF/pluginIcon.svg", javaClass)

    override fun getSelectedValue(): String {
        val status = project.getService(EnforcerService::class.java).currentStatus
        val total = status.cleanedDependencies.get()
        return if (total > 0) "$total Bereinigungen" else "MCE Aktiv"
    }

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { event ->
        val popup = createStatusPopup()
        popup.showInCenterOf(event.component)
    }

    override fun getPopupStep() = null
    override fun install(statusBar: StatusBar) {}
    override fun dispose() {}

    private fun createStatusPopup(): com.intellij.openapi.ui.popup.JBPopup {
        val service = project.getService(EnforcerService::class.java)
        val settingsService = project.getService(EnforcerSettingsStateService::class.java)
        val status = service.currentStatus

        // Main Panel
        val mainPanel = JPanel(BorderLayout(0, 10)).apply {
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        }

        // Header Panel mit Controls (Titel, Rerun, Settings)
        val headerPanel = JPanel(BorderLayout())
        val titleLabel = JBLabel("Maven Consistency Enforcer").apply {
            font = font.deriveFont(java.awt.Font.BOLD, font.size + 2f)
        }
        headerPanel.add(titleLabel, BorderLayout.WEST)

        // Button Group (Rerun & Settings)
        val toolbarPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0))
        
        // Rerun-Button (Nutzt IntelliJ AllIcons)
        val rerunButton = JButton(AllIcons.Actions.Refresh).apply {
            toolTipText = "Konsistenzprüfung manuell starten"
            isBorderPainted = false
            isContentAreaFilled = false
            addActionListener {
                // Führt den Check im Hintergrund-Thread aus, damit die IDE nicht freezt
                ApplicationManager.getApplication().executeOnPooledThread {
                    service.runFullConsistencyCheck()
                }
            }
        }
        
        // Settings Zahnrad-Button
        val settingsButton = JButton(AllIcons.General.GearPlain).apply {
            toolTipText = "MCE Einstellungen öffnen"
            isBorderPainted = false
            isContentAreaFilled = false
            addActionListener {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, EnforcerSettingsConfigurable::class.java)
            }
        }
        
        toolbarPanel.add(rerunButton)
        toolbarPanel.add(settingsButton)
        headerPanel.add(toolbarPanel, BorderLayout.EAST)
        mainPanel.add(headerPanel, BorderLayout.NORTH)

        // Content Panel (Progress & Metriken)
        val contentPanel = JPanel(BorderLayout(0, 8))

        // Ladebalken (Abgesichert gegen 0 Divisionen)
        val maxProgress = status.totalDependenciesToProcess.coerceAtLeast(1)
        val currentProgress = status.cleanedDependencies.get().coerceAtMost(maxProgress)
        val progressBar = JProgressBar(0, maxProgress).apply {
            value = currentProgress
            isStringPainted = true
            string = "${status.cleanedDependencies.get()} Elemente verarbeitet"
        }
        contentPanel.add(progressBar, BorderLayout.NORTH)

        // Grid für die Metriken
        val metricsPanel = JPanel(GridLayout(0, 2, 10, 4))
        
        // Globaler Quick Toggle
        metricsPanel.add(JBLabel("Plugin-Status:"))
        val toggleEnable = JBCheckBox("Aktiv", settingsService.state.isEnabled).apply {
            addActionListener {
                settingsService.state.isEnabled = this.isSelected
            }
        }
        metricsPanel.add(toggleEnable)

        metricsPanel.add(JBLabel("Überprüfte Module:"))
        metricsPanel.add(JBLabel(status.checkedModules.get().toString()))

        metricsPanel.add(JBLabel("Ignorierte Module:"))
        metricsPanel.add(JBLabel(status.ignoredModules.get().toString()))

        metricsPanel.add(JBLabel("Durchgeführte Enforcements:"))
        metricsPanel.add(JBLabel("${status.enforcementsCount.get()} Ersetzungen"))

        metricsPanel.add(JBLabel("Letzter Durchlauf:"))
        metricsPanel.add(JBLabel("${status.lastUpdated} (${status.durationMs} ms)"))

        contentPanel.add(metricsPanel, BorderLayout.CENTER)
        mainPanel.add(contentPanel, BorderLayout.CENTER)

        // Details-Sektion (Die Ersetzungs-Historie "Wo")
        val locations = status.enforcementLocations.toList()
        if (locations.isNotEmpty()) {
            val detailsPanel = JPanel(BorderLayout(0, 4))
            detailsPanel.add(JBLabel("Details der Ersetzungen (libEntry -> Modul):").apply {
                font = font.deriveFont(java.awt.Font.BOLD)
            }, BorderLayout.NORTH)

            val jbList = JBList(locations).apply {
                cellRenderer = DefaultListCellRenderer() // Sauberes Standard-Rendering der Strings
            }
            
            val scrollPane = JBScrollPane(jbList).apply {
                preferredSize = Dimension(380, 120)
                border = IdeBorderFactory.createBorder(SideBorder.ALL)
            }
            detailsPanel.add(scrollPane, BorderLayout.CENTER)
            mainPanel.add(detailsPanel, BorderLayout.SOUTH)
        } else if (status.checkedModules.get() > 0) {
            val allGoodLabel = JBLabel("Alle Module sind konsistent.", AllIcons.General.InspectionsOK, SwingConstants.LEFT)
            mainPanel.add(allGoodLabel, BorderLayout.SOUTH)
        }

        return JBPopupFactory.getInstance()
            .createComponentPopupBuilder(mainPanel, null)
            .setMovable(true)
            .setRequestFocus(true)
            .createPopup()
    }
}

