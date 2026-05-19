package dev.silverhorn.fca.maven_consistency_enforcer.settings

import com.intellij.openapi.components.*

@State(
    name = "dev.silverhorn.fca.maven_consistency_enforcer.settings.MceSettings",
    storages = [Storage("mce-settings.xml")]
)
@Service(Service.Level.PROJECT)
class EnforcerSettingsStateService : PersistentStateComponent<EnforcerSettingsState>{

    private var settingsState = EnforcerSettingsState()

    override fun getState(): EnforcerSettingsState = settingsState

    public fun getSettings(): EnforcerSettingsState {
        return settingsState
    }

    override fun loadState(state: EnforcerSettingsState) {
        settingsState = state
    }
}