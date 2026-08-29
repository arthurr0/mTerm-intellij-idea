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
        var notifyEnabled: Boolean = true
        var notifyOnlyWhenIdeUnfocused: Boolean = true
        var restoreLayout: Boolean = true
        var showActivityIndicator: Boolean = true
        var highlightFocusedPane: Boolean = true
        var trackAgentChanges: Boolean = true
        var changeRetentionDays: Int = 7
        var enabledAgentNames: MutableSet<String> = mutableSetOf()
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

    var notifyEnabled: Boolean
        get() = state.notifyEnabled
        set(value) { state.notifyEnabled = value }

    var notifyOnlyWhenIdeUnfocused: Boolean
        get() = state.notifyOnlyWhenIdeUnfocused
        set(value) { state.notifyOnlyWhenIdeUnfocused = value }

    var restoreLayout: Boolean
        get() = state.restoreLayout
        set(value) { state.restoreLayout = value }

    var showActivityIndicator: Boolean
        get() = state.showActivityIndicator
        set(value) { state.showActivityIndicator = value }

    var highlightFocusedPane: Boolean
        get() = state.highlightFocusedPane
        set(value) { state.highlightFocusedPane = value }

    var trackAgentChanges: Boolean
        get() = state.trackAgentChanges
        set(value) { state.trackAgentChanges = value }

    var changeRetentionDays: Int
        get() = state.changeRetentionDays
        set(value) { state.changeRetentionDays = value }

    fun consumeLegacyAgentNames(): Set<String>? {
        val names = state.enabledAgentNames
        if (names.isEmpty()) return null
        val copy = names.toSet()
        names.clear()
        return copy
    }

    companion object {
        fun getInstance(): MTermSettings = service()
    }
}
