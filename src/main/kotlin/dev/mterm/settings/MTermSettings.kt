package dev.mterm.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import dev.mterm.AgentKind
import dev.mterm.sound.AgentSound

@Service(Service.Level.APP)
@State(name = "MTermSettings", storages = [Storage("mterm.xml")])
class MTermSettings : PersistentStateComponent<MTermSettings.State> {

    class State {
        var soundEnabled: Boolean = true
        var soundId: String = AgentSound.CHIME.name
        var soundForShell: Boolean = false
        var reflectAgentTitle: Boolean = true
        var enabledAgentNames: MutableSet<String> = mutableSetOf()
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(loaded: State) {
        state = loaded
        ensureAllAgentsEnabledByDefault()
    }

    private fun ensureAllAgentsEnabledByDefault() {
        val allNames = AgentKind.entries.map { it.name }.toSet()
        if (state.enabledAgentNames.isEmpty()) {
            state.enabledAgentNames.addAll(allNames)
        } else {
            allNames.forEach { name ->
                if (!state.enabledAgentNames.contains(name)) {
                    state.enabledAgentNames.add(name)
                }
            }
        }
    }

    var soundEnabled: Boolean
        get() = state.soundEnabled
        set(value) { state.soundEnabled = value }

    var sound: AgentSound
        get() = AgentSound.fromId(state.soundId)
        set(value) { state.soundId = value.name }

    var soundForShell: Boolean
        get() = state.soundForShell
        set(value) { state.soundForShell = value }

    var reflectAgentTitle: Boolean
        get() = state.reflectAgentTitle
        set(value) { state.reflectAgentTitle = value }

    fun isAgentEnabled(kind: AgentKind): Boolean =
        state.enabledAgentNames.contains(kind.name)

    fun setAgentEnabled(kind: AgentKind, enabled: Boolean) {
        if (enabled) {
            state.enabledAgentNames.add(kind.name)
        } else {
            state.enabledAgentNames.remove(kind.name)
        }
    }

    companion object {
        fun getInstance(): MTermSettings = service()
    }
}
