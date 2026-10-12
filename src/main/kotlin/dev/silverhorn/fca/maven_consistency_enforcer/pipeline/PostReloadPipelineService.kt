package dev.silverhorn.fca.maven_consistency_enforcer.pipeline

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompileScope
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.Alarm
import dev.silverhorn.fca.maven_consistency_enforcer.EnforcerBundle
import dev.silverhorn.fca.maven_consistency_enforcer.notifications.MceNotification
import dev.silverhorn.fca.maven_consistency_enforcer.service.EnforcerService
import dev.silverhorn.fca.maven_consistency_enforcer.settings.CompileModuleScope
import dev.silverhorn.fca.maven_consistency_enforcer.settings.CompileSourceScope
import dev.silverhorn.fca.maven_consistency_enforcer.settings.CompileType
import dev.silverhorn.fca.maven_consistency_enforcer.settings.EnforcerSettingsStateService
import dev.silverhorn.fca.maven_consistency_enforcer.ui.MceStatusBarWidget

/**
 * Orchestriert die Folgeprozesse nach dem Maven-Reload / Enforcement:
 *
 *   compile -> run configurations
 *
 * Die Schritte sind einzeln ab-/zuschaltbar, bedingt ausf?hrbar (nur bei ?nderungen),
 * fail-fast-f?hig (Abbruch bei Compile-Fehlern) und entprellt gegen mehrfaches Triggern.
 */
@Service(Service.Level.PROJECT)
class PostReloadPipelineService(private val project: Project) {

    private val logger = Logger.getInstance(PostReloadPipelineService::class.java)
    private val settings: EnforcerSettingsStateService by lazy { project.service() }
    private val moduleManager: ModuleManager by lazy { project.service() }

    // Entprellung: Schnelle, aufeinanderfolgende Reloads l?sen nur einen Lauf aus.
    private val debounceAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)

    /**
     * Wird vom [EnforcerService] nach Abschluss des Enforcements aufgerufen.
     * Entprellt und startet dann die Pipeline (sofern aktiviert).
     *
     * @param changeCount Anzahl der durch das Enforcement vorgenommenen ?nderungen.
     * @param changedModules Namen der Module, die ver?ndert wurden (f?r CHANGED_MODULES-Scope).
     */
    fun onEnforcementFinished(changeCount: Int, changedModules: Set<String>) {
        val state = settings.state
        if (!state.isEnabled || !state.enablePostReloadPipeline) return

        if (state.onlyRunPipelineOnChanges && changeCount <= 0) {
            logger.info(EnforcerBundle.message("pipeline.notification.skippedNoChanges"))
            return
        }

        val delay = state.pipelineDebounceMs.coerceAtLeast(0)
        debounceAlarm.cancelAllRequests()
        debounceAlarm.addRequest({ runPipeline(changedModules, manual = false) }, delay)
    }

    /**
     * Startet die Pipeline unmittelbar (z. B. ?ber die manuelle "Run Pipeline Now"-Action).
     */
    fun runPipelineManually() {
        val changedModules = project.service<EnforcerService>().currentStatus.enforcedDependencies.keys.toSet()
        debounceAlarm.cancelAllRequests()
        runPipeline(changedModules, manual = true)
    }

    private fun runPipeline(changedModules: Set<String>, manual: Boolean) {
        val state = settings.state
        if (!state.enablePostReloadPipeline && !manual) return

        val status = project.service<EnforcerService>().currentStatus
        status.resetPipeline()
        status.pipelineRan = true
        val startTime = System.currentTimeMillis()

        logger.info(EnforcerBundle.message("pipeline.notification.started"))

        val afterCompile: () -> Unit = {
            if (state.enableRunConfigStep) {
                launchRunConfigurations(state.postRunConfigurations, state.runConfigUseDebug)
            }
            status.pipelineDurationMs = System.currentTimeMillis() - startTime
            updateStatusBar()
            MceNotification.showInfo(
                project,
                EnforcerBundle.message("pipeline.finished", status.pipelineDurationMs)
            )
        }

        if (state.enableCompileStep) {
            runCompileStep(changedModules, manual, onDone = { aborted, errors ->
                if (state.failFast && (aborted || errors > 0)) {
                    status.pipelineDurationMs = System.currentTimeMillis() - startTime
                    updateStatusBar()
                    MceNotification.showWarning(
                        project,
                        EnforcerBundle.message("pipeline.compile.failed", errors)
                    )
                } else {
                    afterCompile()
                }
            })
        } else {
            afterCompile()
        }
    }

    // --- Compile-Schritt -----------------------------------------------------

    private fun runCompileStep(
        changedModules: Set<String>,
        manual: Boolean,
        onDone: (aborted: Boolean, errors: Int) -> Unit
    ) {
        val state = settings.state
        val modules = resolveModules(changedModules, state.compileModuleScope, manual)

        if (modules.isEmpty()) {
            MceNotification.showInfo(project, EnforcerBundle.message("pipeline.compile.noModules"))
            onDone(false, 0)
            return
        }

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val compilerManager = CompilerManager.getInstance(project)
            val includeTests = state.compileSourceScope == CompileSourceScope.WITH_TESTS
            val scope: CompileScope = compilerManager.createModulesCompileScope(
                modules.toTypedArray(),
                /* includeDependentModules = */ true,
                /* includeRuntimeDependencies = */ false,
                /* includeTests = */ includeTests
            )

            MceNotification.showInfo(
                project,
                EnforcerBundle.message("pipeline.compile.started", modules.size)
            )

            val callback = com.intellij.openapi.compiler.CompileStatusNotification { aborted, errors, warnings, _ ->
                val status = project.service<EnforcerService>().currentStatus
                status.lastCompileErrors.set(errors)
                status.lastCompileWarnings.set(warnings)
                status.lastCompileAborted = aborted
                updateStatusBar()

                if (aborted) {
                    MceNotification.showWarning(project, EnforcerBundle.message("pipeline.compile.aborted"))
                } else {
                    MceNotification.showInfo(
                        project,
                        EnforcerBundle.message("pipeline.compile.success", errors, warnings)
                    )
                }
                onDone(aborted, errors)
            }

            when (state.compileType) {
                CompileType.BUILD -> compilerManager.make(scope, callback)
                CompileType.REBUILD -> compilerManager.compile(scope, callback)
            }
        }
    }

    private fun resolveModules(
        changedModules: Set<String>,
        scope: CompileModuleScope,
        manual: Boolean
    ): List<Module> {
        val allModules = moduleManager.modules.toList()
        return when (scope) {
            CompileModuleScope.WHOLE_PROJECT -> allModules
            CompileModuleScope.CHANGED_MODULES -> {
                val selected = allModules.filter { changedModules.contains(it.name) }
                // Fallback: Bei manueller Ausf?hrung ohne bekannte ?nderungen das ganze Projekt bauen.
                if (selected.isEmpty() && manual) allModules else selected
            }
        }
    }

    // --- Run-Configuration-Schritt ------------------------------------------

    private fun launchRunConfigurations(names: List<String>, useDebug: Boolean) {
        if (names.isEmpty()) {
            MceNotification.showInfo(project, EnforcerBundle.message("pipeline.runConfig.none"))
            return
        }

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val runManager = RunManager.getInstance(project)
            val executor = if (useDebug) {
                DefaultDebugExecutor.getDebugExecutorInstance()
            } else {
                DefaultRunExecutor.getRunExecutorInstance()
            }
            val status = project.service<EnforcerService>().currentStatus

            for (name in names) {
                val settingsEntry = runManager.allSettings.firstOrNull { it.name == name }
                if (settingsEntry == null) {
                    MceNotification.showWarning(
                        project,
                        EnforcerBundle.message("pipeline.runConfig.notFound", name)
                    )
                    continue
                }
                MceNotification.showInfo(
                    project,
                    EnforcerBundle.message("pipeline.runConfig.launching", name)
                )
                ProgramRunnerUtil.executeConfiguration(settingsEntry, executor)
                status.launchedRunConfigs.add(name)
            }
            updateStatusBar()
        }
    }

    private fun updateStatusBar() {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                (WindowManager.getInstance().getStatusBar(project)
                    ?.getWidget(MceStatusBarWidget.ID) as? MceStatusBarWidget)?.updateLabelText()
            }
        }
    }
}

