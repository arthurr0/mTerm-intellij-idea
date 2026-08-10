package dev.mterm.agents

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic
import dev.mterm.settings.MTermSettings
import java.awt.Color
import java.util.UUID

@Service(Service.Level.APP)
@State(name = "MTermAgents", storages = [Storage("mterm.xml")])
class AgentRegistry : PersistentStateComponent<AgentRegistry.State> {

    class State {
        var profiles: MutableList<ProfileState> = mutableListOf()
        var initialized: Boolean = false
    }

    class ProfileState {
        var id: String = ""
        var displayName: String = ""
        var command: String? = null
        var glyph: String = "❯"
        var colorRgb: Int = 0xCFD3D8
        var workingDirectory: String? = null
        var builtIn: Boolean = false
        var enabled: Boolean = true
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(loaded: State) {
        state = loaded
        ensureBuiltIns()
    }

    override fun initializeComponent() {
        ensureBuiltIns()
    }

    private fun ensureBuiltIns() {
        if (!state.initialized && state.profiles.isEmpty()) {
            val legacy = MTermSettings.getInstance().consumeLegacyAgentNames()
            state.profiles = AgentProfile.builtIns()
                .map { profile -> profile.copy(enabled = legacy?.contains(profile.id) ?: true).toState() }
                .toMutableList()
            state.initialized = true
            return
        }
        val known = state.profiles.map { it.id }.toSet()
        for (builtIn in AgentProfile.builtIns()) {
            if (builtIn.id !in known) state.profiles.add(builtIn.toState())
        }
        state.initialized = true
    }

    fun profiles(): List<AgentProfile> {
        ensureBuiltIns()
        return state.profiles.map { it.toProfile() }
    }

    fun enabledProfiles(): List<AgentProfile> = profiles().filter { it.enabled }

    fun find(id: String?): AgentProfile? = id?.let { key -> profiles().firstOrNull { it.id == key } }

    fun findOrFirst(id: String?): AgentProfile? = find(id) ?: enabledProfiles().firstOrNull() ?: profiles().firstOrNull()

    fun replaceAll(profiles: List<AgentProfile>) {
        state.profiles = profiles.map { it.toState() }.toMutableList()
        state.initialized = true
        ensureBuiltIns()
        ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).agentsChanged()
    }

    fun newProfileId(): String = "custom-" + UUID.randomUUID().toString().take(8)

    fun interface Listener {
        fun agentsChanged()
    }

    companion object {
        @JvmStatic
        val TOPIC: Topic<Listener> = Topic.create("mTerm agents changed", Listener::class.java)

        fun getInstance(): AgentRegistry = service()
    }
}

private fun AgentProfile.toState(): AgentRegistry.ProfileState = AgentRegistry.ProfileState().also {
    it.id = id
    it.displayName = displayName
    it.command = command
    it.glyph = glyph
    it.colorRgb = color.rgb and 0xFFFFFF
    it.workingDirectory = workingDirectory
    it.builtIn = builtIn
    it.enabled = enabled
}

private fun AgentRegistry.ProfileState.toProfile(): AgentProfile = AgentProfile(
    id = id,
    displayName = displayName.ifBlank { id },
    command = command?.takeIf { it.isNotBlank() },
    glyph = glyph.ifBlank { "❯" },
    color = Color(colorRgb),
    workingDirectory = workingDirectory?.takeIf { it.isNotBlank() },
    builtIn = builtIn,
    enabled = enabled,
)
