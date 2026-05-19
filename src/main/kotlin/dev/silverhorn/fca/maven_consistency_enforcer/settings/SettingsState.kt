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
    var forceLocalModules: Boolean = true,
    var excludedModules: List<String> = emptyList(),
    var logVerbosity: LogLevel = LogLevel.INFO
)