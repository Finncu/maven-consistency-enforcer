package dev.silverhorn.fca.maven_consistency_enforcer.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsStateService
import dev.silverhorn.fca.maven_consistency_enforcer.settings.LogLevel
import java.awt.Dimension
import javax.swing.JComponent

class EnforcerSettingsConfigurable(private val project: Project) : SearchableConfigurable {

    private val settingsService :EnforcerSettingsStateService by lazy { project.service() }
    private val moduleManager :ModuleManager by lazy { project.service() }
    private var uiPanel: com.intellij.openapi.ui.DialogPanel? = null

// Die interaktive Checkbox-Liste für die Module
    private val checkBoxList = CheckBoxList<String>()

    override fun getId(): String = "dev.silverhorn.fca.maven_consistency_enforcer.settings"
    override fun getDisplayName(): String = "Maven Consistency Enforcer"

    override fun createComponent(): JComponent {
        val state = settingsService.state

        // 1. Alle verfügbaren Modulnamen des aktuellen Projekts sortiert abrufen
        val allModules = moduleManager.modules
            .map { it.name }
            .sorted()

        // 2. Die Liste befüllen und den Status (ausgewählt/nicht ausgewählt) setzen
        checkBoxList.clear()
        allModules.forEach { moduleName ->
            // Ein Häkchen bedeutet in unserem UI: "Dieses Modul vom Enforcer ausschließen"
            val isExcluded = state.excludedModules.contains(moduleName)
            checkBoxList.addItem(moduleName, moduleName, isExcluded)
        }

        // 3. UI mittels Kotlin UI DSL v2 aufbauen
        uiPanel = panel {
            row {
                checkBox("Enable Maven Consistency Enforcer")
                    .bindSelected(state::isEnabled)
                checkBox("Enable Enforcement of Local Module Usage")
                .bindSelected(state::forceLocalModules)
            }

            group("Exclusion Rules") {
                row {
                    label("Select modules to exclude from enforcing:")
                }
                row {
                    // Wir packen die CheckBoxList in einen scrollbaren Container mit fester Mindestgröße
                    val scrollPane = JBScrollPane(checkBoxList).apply {
                        preferredSize = Dimension(400, 200)
                    }
                    cell(scrollPane)
                        .align(AlignX.FILL)
                        .comment("Checked modules will not be modified by the consistency enforcer.")
                }
            }

            group("Logging") {
                row("Log Level:") {
                    comboBox(LogLevel.values().toList())
                        .bindItem(
                            getter = { state.logVerbosity },
                            setter = { value -> state.logVerbosity = value ?: LogLevel.INFO }
                        )
                }
            }
        }

        return uiPanel!!
    }

    override fun isModified(): Boolean {
        if (uiPanel?.isModified() == true) return true

        // Prüfen, ob sich die Auswahl in der Checkbox-Liste von den gespeicherten Daten unterscheidet
        val currentExclusions = getSelectedModulesFromUI()
        val savedExclusions = settingsService.state.excludedModules
        return currentExclusions.toSet() != savedExclusions.toSet()
    }

    override fun apply() {
        // Standard-Bindings anwenden (z.B. isEnabled und LogLevel)
        uiPanel?.apply()

        // Die ausgewählten Module aus der UI-Liste auslesen und im State speichern
        settingsService.state.excludedModules = getSelectedModulesFromUI()
    }

    override fun reset() {
        uiPanel?.reset()

        // UI-Liste auf den gespeicherten Stand zurücksetzen
        val state = settingsService.state
        for (i in 0 until checkBoxList.itemsCount) {
            val moduleName = checkBoxList.getItemAt(i) ?: continue
            checkBoxList.setItemSelected(moduleName, state.excludedModules.contains(moduleName))
        }
    }

    override fun disposeUIResources() {
        uiPanel = null
    }

    /**
     * Hilfsfunktion, die alle vom Benutzer angehakten Modulnamen aus der Liste extrahiert.
     */
    private fun getSelectedModulesFromUI(): List<String> {
        val selected = mutableListOf<String>()
        for (i in 0 until checkBoxList.itemsCount) {
            val moduleName = checkBoxList.getItemAt(i) ?: continue
            if (checkBoxList.isItemSelected(i)) {
                selected.add(moduleName)
            }
        }
        return selected
    }
}