package dev.silverhorn.fca.maven_consistency_enforcer.settings

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

enum class MavenReloadType {
    SCHEDULE_IMPORT_RESOLVE, FORCED//, SYNC
}

/**
 * Bestimmt, welche Module der Compile-Schritt der Pipeline bearbeitet.
 * - [CHANGED_MODULES]: nur die durch das Enforcement ver?nderten Module
 * - [WHOLE_PROJECT]: das gesamte Projekt
 */
enum class CompileModuleScope {
    CHANGED_MODULES, WHOLE_PROJECT
}

/**
 * Bestimmt, welche Quell-Roots kompiliert werden.
 * - [WITH_TESTS]: Produktions- und Test-Sources
 * - [WITHOUT_TESTS]: nur Produktions-Sources
 */
enum class CompileSourceScope {
    WITH_TESTS, WITHOUT_TESTS
}

/**
 * Bestimmt die Compile-Semantik.
 * - [BUILD]: inkrementelles Kompilieren (nur ge?nderte Dateien)
 * - [REBUILD]: erzwingt das Neukompilieren des gesamten Scopes
 */
enum class CompileType {
    BUILD, REBUILD
}

/**
 * H?lt den Zustand der Konfiguration. Die Standardwerte entsprechen deinem
 * bisherigen Plugin-Standardverhalten.
 */
data class EnforcerSettingsState(
    var isEnabled: Boolean = true,
    var enforceModuleLinking: Boolean? = null,
    var excludedModules: List<String> = emptyList(),
    var logLevel: LogLevel = LogLevel.INFO,
    var mavenReloadType: MavenReloadType = MavenReloadType.FORCED,

    /** F?hrt beim Starten des Projekts eine Konsistenzpr?fung durch. */
    var runInitialHealthCheck: Boolean? = null,

    // --- Post-Reload Automation Pipeline ---

    /** Master-Schalter f?r die Folgeprozess-Pipeline nach dem Enforcement. */
    var enablePostReloadPipeline: Boolean? = null,

    /** F?hrt die Folgeschritte nur aus, wenn das Enforcement etwas ver?ndert hat. */
    var onlyRunPipelineOnChanges: Boolean = true,

    /** Bricht die Pipeline bei Compile-Fehlern ab (fail-fast). */
    var failFast: Boolean = true,

    /** Entprellung in Millisekunden gegen mehrfaches Triggern bei schnellen Reloads. */
    var pipelineDebounceMs: Int = 2000,

    // --- Compile-Schritt ---

    /** Aktiviert den automatischen Compile-Schritt nach dem Reload. */
    var enableCompileStep: Boolean? = null,

    /** Welche Module kompiliert werden (nur ge?nderte oder das ganze Projekt). */
    var compileModuleScope: CompileModuleScope = CompileModuleScope.WHOLE_PROJECT,

    /** Ob Test-Sources mitkompiliert werden. */
    var compileSourceScope: CompileSourceScope = CompileSourceScope.WITHOUT_TESTS,

    /** Build (inkrementell) oder Rebuild (erzwungen). */
    var compileType: CompileType = CompileType.BUILD,

    // --- Run-Configuration-Schritt ---

    /** Aktiviert das Starten von Run-Configurations nach erfolgreichem Compile. */
    var enableRunConfigStep: Boolean = false,

    /** Namen der zu startenden Run-Configurations. */
    var postRunConfigurations: List<String> = emptyList(),

    /** Startet die ausgew?hlten Run-Configurations im Debug-Modus. */
    var runConfigUseDebug: Boolean = false
)