package dev.silverhorn.fca.maven_consistency_enforcer.settings

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

/**
 * Hält den Zustand der Konfiguration. Die Standardwerte entsprechen deinem
 * bisherigen Plugin-Standardverhalten.
 */
data class EnforcerSettingsState(
    var isEnabled: Boolean = true,
    var enforceModuleLinking: Boolean? = null,
    var excludedModules: List<String> = emptyList(),
    var logLevel: LogLevel = LogLevel.INFO,

    /** Führt beim Starten des Projekts eine Konsistenzprüfung durch. */
    var runInitialHealthCheck: Boolean? = null
)