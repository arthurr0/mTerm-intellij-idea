package dev.mterm.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import dev.mterm.sound.AgentSound

@Service(Service.Level.APP)
@State(name = "MTermSettings", storages = [Storage("mterm.xml")])
class MTermSettings : PersistentStateComponent<MTermSettings.State> {

    class State {
        var soundEnabled: Boolean = true
        var soundId: String = AgentSound.CHIME.name
        var soundForShell: Boolean = false
        var reflectAgentTitle: Boolean = true
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(loaded: State) {
        state = loaded
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

    companion object {
        fun getInstance(): MTermSettings = service()
    }
}
