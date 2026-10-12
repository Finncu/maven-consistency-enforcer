package dev.silverhorn.fca.maven_consistency_enforcer.settings

import com.intellij.execution.RunManager
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.CheckBoxList
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.and
import dev.silverhorn.fca.maven_consistency_enforcer.EnforcerBundle
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

class EnforcerSettingsConfigurable(private val project: Project) : SearchableConfigurable {

    private val settingsService: EnforcerSettingsStateService by lazy { project.service() }
    private val moduleManager: ModuleManager by lazy { project.service() }
    private var uiPanel: com.intellij.openapi.ui.DialogPanel? = null

    // Die interaktive Checkbox-Liste f?r die Module
    private val checkBoxList = CheckBoxList<String>()

    // Checkbox-Liste f?r die in der Pipeline zu startenden Run-Configurations
    private val runConfigCheckBoxList = CheckBoxList<String>()

    // Suchfeld zum Filtern der Run-Configurations
    private val runConfigSearchField = SearchTextField(false)

    // H?lt die Auswahl aller Run-Configurations unabh?ngig von der aktuellen Filterung.
    private val runConfigSelection = linkedMapOf<String, Boolean>()

    override fun getId(): String = "dev.silverhorn.fca.maven_consistency_enforcer.settings"
    override fun getDisplayName(): String = EnforcerBundle.message("settings.configurable.displayName")

    override fun createComponent(): JComponent {
        val state = settingsService.state

        // 1. Alle verf?gbaren Modulnamen des aktuellen Projekts sortiert abrufen
        val allModules = moduleManager.modules
            .map { it.name }
            .sorted()

        // 2. Die Liste bef?llen und den Status (ausgew?hlt/nicht ausgew?hlt) setzen
        checkBoxList.clear()
        allModules.forEach { moduleName ->
            val isExcluded = state.excludedModules.contains(moduleName)
            checkBoxList.addItem(moduleName, moduleName, isExcluded)
        }

        // 2b. Verf?gbare Run-Configurations abrufen und Auswahl-Map initialisieren
        val allRunConfigs = RunManager.getInstance(project).allSettings
            .map { it.name }
            .sorted()
        runConfigSelection.clear()
        allRunConfigs.forEach { name ->
            runConfigSelection[name] = state.postRunConfigurations.contains(name)
        }

        // Toggles aus der (gefilterten) Liste in die Auswahl-Map zur?ckschreiben
        runConfigCheckBoxList.setCheckBoxListListener { index, value ->
            val name = runConfigCheckBoxList.getItemAt(index) ?: return@setCheckBoxListListener
            runConfigSelection[name] = value
        }

        // Suchfeld: filtert die Liste live bei jeder Eingabe
        runConfigSearchField.textEditor.emptyText.text =
            EnforcerBundle.message("settings.configurable.pipeline.runConfig.search.placeholder")
        runConfigSearchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                repopulateRunConfigList(runConfigSearchField.text)
            }
        })
        runConfigSearchField.text = ""
        repopulateRunConfigList("")

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
                initialMavenSyncCb =
                    checkBox(EnforcerBundle.message("settings.configurable.enableMavenSyncFix.checkbox"))
                        .bindSelected(
                            { state.runInitialHealthCheck ?: false }, // Getter: null wird zu false
                            { state.runInitialHealthCheck = it }      // Setter: schreibt normales Boolean zur?ck
                        )
                        // Diese Checkbox graut sich automatisch aus, wenn der Hauptschalter aus ist
                        .enabledIf(mainEnforcerCb.selected)
                        .apply { component.name = "initialMavenSyncCb" }
            }

            row {
                moduleEnforcingCb =
                    checkBox(EnforcerBundle.message("settings.configurable.enableLocalModuleUsage.checkbox"))
                        .bindSelected(
                            { state.enforceModuleLinking ?: false }, // Getter: null wird zu false
                            { state.enforceModuleLinking = it }      // Setter: schreibt normales Boolean zur?ck
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
                            setter = { value -> state.mavenReloadType = value ?: MavenReloadType.FORCED }
                        )
                }
            }

            group(EnforcerBundle.message("settings.configurable.pipeline.group")) {
                lateinit var pipelineCb: Cell<com.intellij.ui.components.JBCheckBox>
                lateinit var compileCb: Cell<com.intellij.ui.components.JBCheckBox>
                lateinit var runConfigCb: Cell<com.intellij.ui.components.JBCheckBox>

                row {
                    pipelineCb = checkBox(EnforcerBundle.message("settings.configurable.pipeline.enable.checkbox"))
                        .bindSelected(
                            { state.enablePostReloadPipeline ?: false }, // Getter: null wird zu false
                            { state.enablePostReloadPipeline = it }      // Setter: schreibt normales Boolean zur?ck)
                        ).enabledIf(mainEnforcerCb.selected)
                        .apply { component.name = "pipelineCb" }
                }
                row {
                    checkBox(EnforcerBundle.message("settings.configurable.pipeline.onlyOnChanges.checkbox"))
                        .bindSelected(state::onlyRunPipelineOnChanges)
                        .enabledIf(pipelineCb.selected)
                }
                row {
                    checkBox(EnforcerBundle.message("settings.configurable.pipeline.failFast.checkbox"))
                        .bindSelected(state::failFast)
                        .enabledIf(pipelineCb.selected)
                }
                row(EnforcerBundle.message("settings.configurable.pipeline.debounce.label")) {
                    intTextField(0..600000)
                        .bindIntText(state::pipelineDebounceMs)
                        .enabledIf(pipelineCb.selected)
                }

                // --- Compile-Schritt ---
                row {
                    compileCb =
                        checkBox(EnforcerBundle.message("settings.configurable.pipeline.compile.enable.checkbox"))
                            .bindSelected(
                                { state.enableCompileStep ?: false }, // Getter: null wird zu false
                                { state.enableCompileStep = it }      // Setter: schreibt normales Boolean zur?ck
                            )
                            .enabledIf(pipelineCb.selected)
                    .apply { component.name = "compileCb" }
                }
                indent {
                    row(EnforcerBundle.message("settings.configurable.pipeline.compile.moduleScope.label")) {
                        comboBox(CompileModuleScope.entries)
                            .bindItem(
                                getter = { state.compileModuleScope },
                                setter = { value ->
                                    state.compileModuleScope = value ?: CompileModuleScope.CHANGED_MODULES
                                }
                            )
                            .enabledIf(pipelineCb.selected.and(compileCb.selected))
                    }
                    row(EnforcerBundle.message("settings.configurable.pipeline.compile.scope.label")) {
                        comboBox(CompileSourceScope.entries)
                            .bindItem(
                                getter = { state.compileSourceScope },
                                setter = { value ->
                                    state.compileSourceScope = value ?: CompileSourceScope.WITHOUT_TESTS
                                }
                            )
                            .enabledIf(pipelineCb.selected.and(compileCb.selected))
                    }
                    row(EnforcerBundle.message("settings.configurable.pipeline.compile.type.label")) {
                        comboBox(CompileType.entries)
                            .bindItem(
                                getter = { state.compileType },
                                setter = { value -> state.compileType = value ?: CompileType.BUILD }
                            )
                            .enabledIf(pipelineCb.selected.and(compileCb.selected))
                    }
                }

                // --- Run-Configuration-Schritt ---
                row {
                    runConfigCb =
                        checkBox(EnforcerBundle.message("settings.configurable.pipeline.runConfig.enable.checkbox"))
                            .bindSelected(state::enableRunConfigStep)
                            .enabledIf(pipelineCb.selected)
                }
                indent {
                    row {
                        checkBox(EnforcerBundle.message("settings.configurable.pipeline.runConfig.useDebug.checkbox"))
                            .bindSelected(state::runConfigUseDebug)
                            .enabledIf(pipelineCb.selected.and(runConfigCb.selected))
                    }
                    row {
                        label(EnforcerBundle.message("settings.configurable.pipeline.runConfig.label"))
                    }
                    row {
                        cell(runConfigSearchField)
                            .align(AlignX.FILL)
                    }.enabledIf(pipelineCb.selected.and(runConfigCb.selected))
                    row {
                        val runConfigScroll = JBScrollPane(runConfigCheckBoxList).apply {
                            preferredSize = Dimension(400, 160)
                        }
                        cell(runConfigScroll)
                            .align(AlignX.FILL)
                            .comment(EnforcerBundle.message("settings.configurable.pipeline.runConfig.comment"))
                    }.enabledIf(pipelineCb.selected.and(runConfigCb.selected))
                }
            }.enabledIf(mainEnforcerCb.selected)
        }

        return uiPanel!!
    }

    override fun isModified(): Boolean {
        if (uiPanel?.isModified() == true) return true

        val currentExclusions = getSelectedModulesFromUI()
        val savedExclusions = settingsService.state.excludedModules
        if (currentExclusions.toSet() != savedExclusions.toSet()) return true

        val currentRunConfigs = getSelectedRunConfigsFromUI()
        val savedRunConfigs = settingsService.state.postRunConfigurations
        return currentRunConfigs.toSet() != savedRunConfigs.toSet()
    }

    override fun apply() {
        uiPanel?.apply()
        settingsService.state.excludedModules = getSelectedModulesFromUI()
        settingsService.state.postRunConfigurations = getSelectedRunConfigsFromUI()
    }

    override fun reset() {
        uiPanel?.reset()

        val state = settingsService.state
        for (i in 0 until checkBoxList.itemsCount) {
            val moduleName = checkBoxList.getItemAt(i) ?: continue
            checkBoxList.setItemSelected(moduleName, state.excludedModules.contains(moduleName))
        }
        // Auswahl-Map aus dem gespeicherten Zustand neu aufbauen und Liste (ungefiltert) anzeigen
        runConfigSelection.keys.toList().forEach { name ->
            runConfigSelection[name] = state.postRunConfigurations.contains(name)
        }
        runConfigSearchField.text = ""
        repopulateRunConfigList("")
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

    private fun getSelectedRunConfigsFromUI(): List<String> {
        return runConfigSelection.filterValues { it }.keys.toList()
    }

    /**
     * Baut die Run-Config-Checkbox-Liste neu auf und zeigt nur Eintr?ge, deren Name
     * den Filter (case-insensitive) enth?lt. Der Auswahlzustand stammt aus der Map.
     */
    private fun repopulateRunConfigList(filter: String) {
        runConfigCheckBoxList.clear()
        val normalized = filter.trim().lowercase()
        runConfigSelection.forEach { (name, selected) ->
            if (normalized.isEmpty() || name.lowercase().contains(normalized)) {
                runConfigCheckBoxList.addItem(name, name, selected)
            }
        }
    }
}