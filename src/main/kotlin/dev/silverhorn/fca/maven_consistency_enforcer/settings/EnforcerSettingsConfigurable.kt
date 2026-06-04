package dev.silverhorn.fca.maven_consistency_enforcer.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.and
import dev.silverhorn.fca.maven_consistency_enforcer.EnforcerBundle
import java.awt.Dimension
import javax.swing.JComponent

class EnforcerSettingsConfigurable(private val project: Project) : SearchableConfigurable {

    private val settingsService: EnforcerSettingsStateService by lazy { project.service() }
    private val moduleManager: ModuleManager by lazy { project.service() }
    private var uiPanel: com.intellij.openapi.ui.DialogPanel? = null

    // Die interaktive Checkbox-Liste für die Module
    private val checkBoxList = CheckBoxList<String>()

    override fun getId(): String = "dev.silverhorn.fca.maven_consistency_enforcer.settings"
    override fun getDisplayName(): String = EnforcerBundle.message("settings.configurable.displayName")

    override fun createComponent(): JComponent {
        val state = settingsService.state

        // 1. Alle verfügbaren Modulnamen des aktuellen Projekts sortiert abrufen
        val allModules = moduleManager.modules
            .map { it.name }
            .sorted()

        // 2. Die Liste befüllen und den Status (ausgewählt/nicht ausgewählt) setzen
        checkBoxList.clear()
        allModules.forEach { moduleName ->
            val isExcluded = state.excludedModules.contains(moduleName)
            checkBoxList.addItem(moduleName, moduleName, isExcluded)
        }

        // 3. UI mittels Kotlin UI DSL v2 aufbauen
        uiPanel = panel {
            // Wir weisen der Haupt-Checkbox eine Variable zu, um andere Elemente daran zu binden
            lateinit var mainEnforcerCb: Cell<com.intellij.ui.components.JBCheckBox>
            lateinit var moduleEnforcingCb: Cell<com.intellij.ui.components.JBCheckBox>
            lateinit var initialMavenSyncCb: Cell<com.intellij.ui.components.JBCheckBox>

            row {
                mainEnforcerCb = checkBox(EnforcerBundle.message("settings.configurable.enablePlugin.checkbox"))
                    .bindSelected(state::isEnabled)
            }

            row {
                initialMavenSyncCb = checkBox(EnforcerBundle.message("settings.configurable.enableMavenSyncFix.checkbox"))
                    .bindSelected(
                                { state.runInitialHealthCheck ?: false }, // Getter: null wird zu false
                                { state.runInitialHealthCheck = it }      // Setter: schreibt normales Boolean zurück
                            )
                    // Diese Checkbox graut sich automatisch aus, wenn der Hauptschalter aus ist
                    .enabledIf(mainEnforcerCb.selected)
                    .apply { component.name = "initialMavenSyncCb" }
            }

            row {
                moduleEnforcingCb = checkBox(EnforcerBundle.message("settings.configurable.enableLocalModuleUsage.checkbox"))
                    .bindSelected(
                                { state.enforceModuleLinking ?: false }, // Getter: null wird zu false
                                { state.enforceModuleLinking = it }      // Setter: schreibt normales Boolean zurück
                            )
                    // Diese Checkbox graut sich automatisch aus, wenn der Hauptschalter aus ist
                    .enabledIf(mainEnforcerCb.selected)
                    .apply { component.name = "moduleEnforcingCb" }
            }

            // Die gesamte Gruppe deaktiviert sich visuell, wenn das Haupt-Plugin ausgeschaltet ist
            group(EnforcerBundle.message("settings.configurable.exclusionRules.group")) {
                row {
                    label(EnforcerBundle.message("settings.configurable.exclusionRules.label"))
                }
                row {
                    val scrollPane = JBScrollPane(checkBoxList).apply {
                        preferredSize = Dimension(400, 200)
                    }
                    cell(scrollPane)
                        .align(AlignX.FILL)
                        .comment(EnforcerBundle.message("settings.configurable.exclusionRules.comment"))
                }
            }.enabledIf(mainEnforcerCb.selected.and(moduleEnforcingCb.selected))

            group(EnforcerBundle.message("settings.configurable.logging.group")) {
                row(EnforcerBundle.message("settings.configurable.logging.logLevel.label")) {
                    comboBox(LogLevel.entries)
                        .bindItem(
                            getter = { state.logLevel },
                            setter = { value -> state.logLevel = value ?: LogLevel.INFO }
                        )
                }
            }
            group(EnforcerBundle.message("settings.configurable.mavenReload.group")) {
                row(EnforcerBundle.message("settings.configurable.mavenReload.reloadType.label")) {
                    comboBox(MavenReloadType.entries)
                        .bindItem(
                            getter = { state.mavenReloadType },
                            setter = { value -> state.mavenReloadType = value ?: MavenReloadType.SYNC }
                        )
                }
            }
        }

        return uiPanel!!
    }

    override fun isModified(): Boolean {
        if (uiPanel?.isModified() == true) return true

        val currentExclusions = getSelectedModulesFromUI()
        val savedExclusions = settingsService.state.excludedModules
        return currentExclusions.toSet() != savedExclusions.toSet()
    }

    override fun apply() {
        uiPanel?.apply()
        settingsService.state.excludedModules = getSelectedModulesFromUI()
    }

    override fun reset() {
        uiPanel?.reset()

        val state = settingsService.state
        for (i in 0 until checkBoxList.itemsCount) {
            val moduleName = checkBoxList.getItemAt(i) ?: continue
            checkBoxList.setItemSelected(moduleName, state.excludedModules.contains(moduleName))
        }
    }

    override fun disposeUIResources() {
        uiPanel = null
    }

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