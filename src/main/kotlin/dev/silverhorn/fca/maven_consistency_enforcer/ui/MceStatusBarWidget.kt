package dev.silverhorn.fca.maven_consistency_enforcer.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.ui.ClickListener
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.vcsUtil.showAbove
import dev.silverhorn.fca.maven_consistency_enforcer.service.EnforcerService
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsConfigurable
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsStateService
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.event.MouseEvent
import javax.swing.*

class MceStatusBarWidget(private val project: Project) : CustomStatusBarWidget, StatusBarWidget {
    val enforcementService :EnforcerService by lazy { project.service() }
    val settingsService :EnforcerSettingsStateService by lazy { project.service() }


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

        // Ein nativer, unverw?stlicher Swing-Klick-Listener
        object : ClickListener() {
            override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
                try {
                    val popup = createStatusPopup()
                    // Zeigt das Popup exakt an der geklickten Maus-Koordinate (100% verl?sslich)
//                    popup.show(RelativePoint(event))
                    popup.showAbove(this@MceStatusBarWidget.component)
                } catch (e: Throwable) {
                    Messages.showErrorDialog(
                        project,
                        "Fehler beim ?ffnen des Status-Popups:\n${e.message}",
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
            val total = enforcementService.currentStatus.removedAttachedJars.get() + enforcementService.currentStatus.enforcementsCount.get()
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
        var popup: JBPopup? = null
        val status = enforcementService.currentStatus

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
//        val rerunButton = JButton(AllIcons.Actions.Refresh).apply {
//            toolTipText = "trigger manually"
//            isBorderPainted = false
//            isContentAreaFilled = false
//            addActionListener {
//                enforcementService.runFullConsistencyCheck()
//            }
//            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
//        }
        val settingsButton = JButton(AllIcons.General.GearPlain).apply {
            toolTipText = "Einstellungen ?ffnen"
            isBorderPainted = false
            isContentAreaFilled = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, EnforcerSettingsConfigurable::class.java)
                popup?.dispose()
            }
        }
//        toolbarPanel.add(rerunButton)
        toolbarPanel.add(settingsButton)
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
        metricsPanel.add(JBLabel("removed attached jars:"))
        metricsPanel.add(JBLabel("${status.removedAttachedJars.get()}"))
        if (forceLocalModules) {
            metricsPanel.add(JBLabel("checked modules:"))
            metricsPanel.add(JBLabel(status.checkedModules.get().toString()))
            metricsPanel.add(JBLabel("ignored modules:"))
            metricsPanel.add(JBLabel(status.ignoredModules.get().toString()))
            metricsPanel.add(JBLabel("enforced module usage:"))
            metricsPanel.add(JBLabel(status.enforcementsCount.get().toString()).apply {
                if (status.enforcedDependencies.isNotEmpty()) {
                    // Als anklickbaren "Link" darstellen
                    foreground = JBColor(0x589DF6, 0x589DF6)
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    toolTipText = "Details anzeigen"
                    val valueLabel = this
                    object : ClickListener() {
                        override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
                            showEnforcedDependenciesPopup(valueLabel, status.enforcedDependencies)
                            return true
                        }
                    }.installOn(valueLabel)
                }
            })
            metricsPanel.add(JBLabel("processed dependencies:"))
            metricsPanel.add(JBLabel("${status.processedLibraries}"))
        }
        metricsPanel.add(JBLabel("last run:"))
        metricsPanel.add(JBLabel("${status.lastUpdated} (${status.durationMs} ms)"))

        contentPanel.add(metricsPanel, BorderLayout.CENTER)
        mainPanel.add(contentPanel, BorderLayout.CENTER)

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(mainPanel, null)
            .setMovable(true)
            .setRequestFocus(true)
            .setCancelOnClickOutside(true)
            .createPopup()
        return popup
    }

    private fun showEnforcedDependenciesPopup(
        anchor: JComponent,
        enforced: Map<String, MutableList<String>>
    ) {
        if (enforced.isEmpty()) return

        // Flache Zeilen: Module nur in erster Zeile, danach leer (gruppiert wirkend)
        val rows = mutableListOf<Array<String>>()
        enforced.entries
            .sortedBy { it.key }
            .forEach { (module, targets) ->
                targets.toList().sorted().forEachIndexed { idx, target ->
                    rows.add(arrayOf(if (idx == 0) module else "", target))
                }
            }

        val columns = arrayOf<Any>("Module", "Module-Dependency")
        val model = object : javax.swing.table.DefaultTableModel(rows.toTypedArray(), columns) {
            override fun isCellEditable(row: Int, column: Int): Boolean = false
        }
        val table = JBTable(model).apply {
            setShowGrid(false)
            intercellSpacing = java.awt.Dimension(0, 0)
            rowHeight = JBUI.scale(20)
            tableHeader.reorderingAllowed = false
            autoResizeMode = JTable.AUTO_RESIZE_OFF
            autoCreateRowSorter = false
        }
        // Spaltenbreiten anhand des Inhalts approximieren
        val fm = table.getFontMetrics(table.font)
        val col0Width = (rows.maxOfOrNull { fm.stringWidth(it[0]) } ?: 80)
            .coerceAtLeast(fm.stringWidth("Module"))
        val col1Width = (rows.maxOfOrNull { fm.stringWidth(it[1]) } ?: 200)
            .coerceAtLeast(fm.stringWidth("Module-Dependency"))
        table.columnModel.getColumn(0).preferredWidth = col0Width + JBUI.scale(24)
        table.columnModel.getColumn(1).preferredWidth = col1Width + JBUI.scale(24)

        val scroll = JBScrollPane(table).apply {
            border = BorderFactory.createEmptyBorder()
            val prefW = (col0Width + col1Width + JBUI.scale(80)).coerceAtMost(JBUI.scale(900))
            val prefH = (rows.size * table.rowHeight + table.tableHeader.preferredSize.height + JBUI.scale(8))
                .coerceAtMost(JBUI.scale(480))
            preferredSize = java.awt.Dimension(prefW, prefH)
        }

        val container = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(JBLabel("Enforced module dependencies").apply {
                font = font.deriveFont(java.awt.Font.BOLD)
                border = JBUI.Borders.emptyBottom(6)
            }, BorderLayout.NORTH)
            add(scroll, BorderLayout.CENTER)
        }

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(container, table)
            .setTitle("Enforced Module Dependencies")
            .setMovable(true)
            .setResizable(true)
            .setRequestFocus(true)
            .setFocusable(true)
            .setCancelOnClickOutside(true)
            .createPopup()
        popup.show(RelativePoint(anchor, java.awt.Point(0, anchor.height)))
    }
}
